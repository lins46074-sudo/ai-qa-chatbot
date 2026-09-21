package com.aidoc.service.impl;

import com.aidoc.ai.LlmClient;
import com.aidoc.ai.PromptAssembler;
import com.aidoc.common.BusinessException;
import com.aidoc.common.PageResult;
import com.aidoc.common.ResultCode;
import com.aidoc.dto.MessageSendDTO;
import com.aidoc.dto.SessionCreateDTO;
import com.aidoc.entity.ChatMessage;
import com.aidoc.entity.ChatSession;
import com.aidoc.entity.Document;
import com.aidoc.mapper.ChatMessageMapper;
import com.aidoc.mapper.ChatSessionMapper;
import com.aidoc.mapper.DocumentMapper;
import com.aidoc.rag.ChunkIndexService;
import com.aidoc.rag.RagProperties;
import com.aidoc.rag.RetrievedChunk;
import com.aidoc.rag.Retriever;
import com.aidoc.service.ChatService;
import com.aidoc.util.RedisKeys;
import com.aidoc.util.RedisUtil;
import com.aidoc.util.StoragePathUtil;
import com.aidoc.vo.MessageVO;
import com.aidoc.vo.SessionVO;
import com.aidoc.vo.SourceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 问答会话服务实现。
 *
 * <p>围绕「私有文档 + 大模型」的多轮问答，采用<b>检索增强生成（RAG）</b>而非整篇注入，
 * 核心数据流：
 * <pre>
 * 提问 → 校验文档/会话归属 → 保存 USER 消息
 *      → 取该文档切片(Redis 命中 / DB 回源) → 问题向量化 → 余弦召回 TopK
 *      → 阈值过滤 + 相邻合并 + 上下文预算 → 组装带编号的参考资料
 *      → SSE sources 事件下发引用来源
 *      → LLM 流式转发 SSE → 落库 ASSISTANT 全文及其引用来源
 *      → Redis 上下文缓存滑动续期（最近 6 条）
 * </pre></p>
 *
 * <p><b>防幻觉设计</b>：检索零命中时直接把固定文案作为答案返回，
 * <b>不调用大模型</b>（见 aidoc.rag.refuse-when-empty），从源头杜绝无依据生成。</p>
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    /** 上下文缓存保留的最近消息条数 */
    private static final int CTX_KEEP = 6;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final DocumentMapper documentMapper;
    private final RedisUtil redisUtil;
    private final StoragePathUtil storagePathUtil;
    private final LlmClient llmClient;
    private final ChunkIndexService chunkIndexService;
    private final Retriever retriever;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 会话上下文缓存 TTL（分钟，aidoc.redis-ttl.context-minutes） */
    @Value("${aidoc.redis-ttl.context-minutes}")
    private long contextTtlMinutes;

    /** 文档全文缓存 TTL（小时，aidoc.redis-ttl.doc-text-hours） */
    private long docTextTtlHours;

    /**
     * 构造注入。
     *
     * @param chatSessionMapper  会话 Mapper
     * @param chatMessageMapper  消息 Mapper
     * @param documentMapper     文档 Mapper
     * @param redisUtil          Redis 工具
     * @param storagePathUtil    存储路径工具
     * @param llmClient          大模型客户端
     * @param chunkIndexService  切片索引服务
     * @param retriever          检索器
     * @param ragProperties      RAG 配置
     */
    public ChatServiceImpl(ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper,
                           DocumentMapper documentMapper, RedisUtil redisUtil,
                           StoragePathUtil storagePathUtil, LlmClient llmClient,
                           ChunkIndexService chunkIndexService, Retriever retriever,
                           RagProperties ragProperties,
                           @Value("${aidoc.redis-ttl.doc-text-hours}") long docTextTtlHours) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.documentMapper = documentMapper;
        this.redisUtil = redisUtil;
        this.storagePathUtil = storagePathUtil;
        this.llmClient = llmClient;
        this.chunkIndexService = chunkIndexService;
        this.retriever = retriever;
        this.ragProperties = ragProperties;
        this.docTextTtlHours = docTextTtlHours;
    }

    /**
     * 会话分页：id 倒序；一次查询当页全部文档名回填（避免 N+1 查询）。
     */
    @Override
    public PageResult<SessionVO> pageSessions(Long userId, long current, long size) {
        Page<ChatSession> page = chatSessionMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getId));

        List<SessionVO> records = new ArrayList<>(page.getRecords().size());
        // 1. 收集文档 ID 并批量查询（一次 IN 查询替代逐条查）
        if (!page.getRecords().isEmpty()) {
            List<Long> docIds = page.getRecords().stream()
                    .map(ChatSession::getDocumentId).distinct().toList();
            Map<Long, String> docNames = documentMapper.selectBatchIds(docIds).stream()
                    .collect(Collectors.toMap(Document::getId, Document::getFileName));
            // 2. 组装 VO（含 documentName）
            for (ChatSession s : page.getRecords()) {
                records.add(toSessionVO(s, docNames.get(s.getDocumentId())));
            }
        }
        return PageResult.of(page, records);
    }

    /**
     * 新建会话（文档归属校验通过后）。
     */
    @Override
    public SessionVO createSession(SessionCreateDTO dto, Long userId) {
        Document doc = requireReadyDocument(dto.getDocumentId(), userId);
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setDocumentId(doc.getId());
        session.setTitle(StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : "新对话");
        session.setMessageCount(0);
        chatSessionMapper.insert(session);
        return toSessionVO(session, doc.getFileName());
    }

    /**
     * 重命名会话。
     */
    @Override
    public SessionVO renameSession(Long sessionId, String title, Long userId) {
        ChatSession session = requireOwnedSession(sessionId, userId);
        session.setTitle(title);
        chatSessionMapper.updateById(session);
        Document doc = documentMapper.selectById(session.getDocumentId());
        return toSessionVO(session, doc == null ? "" : doc.getFileName());
    }

    /**
     * 删除会话：消息 + Redis 上下文缓存 + 会话行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId, Long userId) {
        requireOwnedSession(sessionId, userId);
        // 1. 删除历史消息
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        // 2. 清理 Redis 上下文缓存（显式删除，避免残留脏上下文）
        redisUtil.delete(RedisKeys.CHAT_CTX + sessionId);
        // 3. 删除会话
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 会话历史消息分页：id 倒序，current=1 即最新一页（配合前端倒序渲染 + 更早消息头部拼接）。
     */
    @Override
    public PageResult<MessageVO> pageMessages(Long sessionId, Long userId, long current, long size) {
        requireOwnedSession(sessionId, userId);
        Page<ChatMessage> page = chatMessageMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId));
        List<MessageVO> records = page.getRecords().stream().map(this::toMessageVO).toList();
        return PageResult.of(page, records);
    }

    /**
     * 第一阶段 prepare：全部校验、检索与落库在此完成，失败直接抛异常（走 JSON 返回），不开 SSE。
     */
    @Override
    public ChatContext prepare(MessageSendDTO dto, Long userId) {
        // 1. 文档校验：存在 + 归属 + 解析就绪
        Document doc = requireReadyDocument(dto.getDocumentId(), userId);

        // 2. 会话校验：为空则自动新建（标题取问题前 15 字）；否则校验归属与文档一致性
        ChatSession session;
        boolean sessionCreated = false;
        if (dto.getSessionId() == null) {
            session = new ChatSession();
            session.setUserId(userId);
            session.setDocumentId(doc.getId());
            session.setTitle(shortTitle(dto.getQuestion()));
            session.setMessageCount(0);
            chatSessionMapper.insert(session);
            sessionCreated = true;
        } else {
            session = requireOwnedSession(dto.getSessionId(), userId);
            if (!session.getDocumentId().equals(doc.getId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "会话与所选文档不匹配，请新建会话后再提问");
            }
        }

        // 3. 落库用户消息并累加会话消息数
        insertMessage(session, "USER", dto.getQuestion());

        // 4. 组装 LLM 消息序列
        ChatContext ctx = new ChatContext();
        ctx.setUserId(userId);
        ctx.setSessionId(session.getId());
        ctx.setDocumentId(doc.getId());
        ctx.setSessionCreated(sessionCreated);
        ctx.setQuestion(dto.getQuestion());

        List<Map<String, String>> messages = new ArrayList<>();
        if (ragProperties.isEnabled()) {
            buildRagContext(ctx, doc, messages);
        } else {
            buildFullTextContext(ctx, doc, messages);
        }
        messages.addAll(loadHistory(session.getId()));
        messages.add(Map.of("role", "user", "content", dto.getQuestion()));
        ctx.setMessages(messages);
        return ctx;
    }

    /**
     * 第二阶段 streamAnswer：流式转发 + 结束后落库助手回复 + 刷新上下文缓存。
     * 内部捕获一切异常（客户端断开 / 模型报错），转 SSE error 事件，绝不向上抛。
     */
    @Override
    public void streamAnswer(ChatContext ctx, SseEmitter emitter) {
        StringBuilder answer = new StringBuilder();
        try {
            // 1. 自动新建的会话先告知前端 sessionId（便于绑定历史与刷新列表）
            if (ctx.isSessionCreated()) {
                sendEvent(emitter, Map.of("event", "session", "sessionId", ctx.getSessionId()));
            }
            // 2. 先下发引用来源，使前端在答案开始输出前就能渲染依据面板
            List<SourceVO> sources = ctx.getSources() == null ? List.of() : ctx.getSources();
            sendEvent(emitter, Map.of("event", "sources", "sources", sources));

            if (ctx.isNoEvidence()) {
                // 3a. 检索零命中且开启拒答：直接回固定文案，不调用大模型（从源头杜绝编造）
                answer.append(PromptAssembler.NO_EVIDENCE_ANSWER);
                sendEvent(emitter, Map.of("event", "delta", "content", answer.toString()));
            } else {
                // 3b. 逐段转发模型增量并累积全文
                llmClient.streamChat(ctx.getMessages(), delta -> {
                    answer.append(delta);
                    try {
                        sendEvent(emitter, Map.of("event", "delta", "content", delta));
                    } catch (IOException ex) {
                        // 客户端已断开：终止流（向上抛出，由外层统一收尾）
                        throw new IllegalStateException("client disconnected", ex);
                    }
                });
            }

            // 4. 流结束：落库助手完整回复及其引用来源（历史消息据此回显依据）
            Long messageId = insertAssistantMessage(ctx.getSessionId(), answer.toString(), sources);
            // 5. 刷新 Redis 上下文缓存（追加本轮两则、仅留最近 CTX_KEEP 条、滑动续期）
            refreshContextCache(ctx, answer.toString());
            // 6. 通知前端结束
            sendEvent(emitter, Map.of("event", "done",
                    "messageId", messageId, "sessionId", ctx.getSessionId()));
        } catch (BusinessException e) {
            // 模型侧业务错误（如未配置 Key）→ 友好提示；已流出的内容不回滚
            log.warn("[问答] 流式过程业务异常: {}", e.getMessage());
            sendEventQuietly(emitter, Map.of("event", "error", "message", e.getMessage()));
        } catch (Exception e) {
            // 客户端断开（IOException 类）或未知异常：尽力发 error 事件
            log.warn("[问答] 流式过程异常: {}", e.getMessage());
            sendEventQuietly(emitter, Map.of("event", "error",
                    "message", "回答生成中断，请稍后重试"));
        } finally {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // emitter 已关闭时忽略
            }
        }
    }

    /* ============================ 上下文组装 ============================ */

    /**
     * RAG 模式：检索文档切片，把命中的片段注入提示词。
     *
     * <p>检索零命中时有两种处理：开启拒答则标记 noEvidence（后续完全不调用大模型）；
     * 关闭拒答则仍调用模型，但提示词中明确「未检索到相关片段」，引导其如实弃答。</p>
     *
     * @param ctx      上下文（回填 sources / noEvidence）
     * @param doc      目标文档
     * @param messages 消息序列（追加 system 消息）
     */
    private void buildRagContext(ChatContext ctx, Document doc, List<Map<String, String>> messages) {
        List<RetrievedChunk> hits = retriever.retrieve(
                ctx.getQuestion(), chunkIndexService.loadChunks(doc.getId()));

        if (hits.isEmpty()) {
            ctx.setSources(List.of());
            if (ragProperties.isRefuseWhenEmpty()) {
                ctx.setNoEvidence(true);
                log.info("[问答] 检索零命中，直接拒答（未调用大模型）, docId={}", doc.getId());
                return;
            }
            messages.add(Map.of("role", "system",
                    "content", PromptAssembler.buildNoEvidenceSystemPrompt(doc.getFileName())));
            return;
        }

        ctx.setSources(hits.stream().map(this::toSourceVO).toList());
        messages.add(Map.of("role", "system",
                "content", PromptAssembler.buildSystemPrompt(doc.getFileName(), hits)));
    }

    /**
     * 全文注入模式（降级路径）：把整篇文档截断后注入提示词。
     *
     * <p>保留该路径用于与 RAG 做效果对比（aidoc.rag.enabled=false 时启用），
     * 缺点是超长文档中间内容会被丢弃，且无法提供引用溯源。</p>
     *
     * @param ctx      上下文
     * @param doc      目标文档
     * @param messages 消息序列（追加 system 消息）
     */
    private void buildFullTextContext(ChatContext ctx, Document doc, List<Map<String, String>> messages) {
        ctx.setSources(List.of());
        String fullText = readDocTextCached(doc);
        messages.add(Map.of("role", "system",
                "content", PromptAssembler.buildFullTextSystemPrompt(doc.getFileName(),
                        PromptAssembler.buildExcerpt(fullText, ragProperties.getMaxContextChars()))));
    }

    /**
     * 读取文档全文：Redis 命中优先，未命中回源磁盘并回填缓存（仅全文注入模式使用）。
     *
     * @param doc 文档
     * @return 文档全文
     */
    private String readDocTextCached(Document doc) {
        String text = redisUtil.getString(RedisKeys.DOC_TEXT + doc.getId());
        if (text != null) {
            return text;
        }
        String loaded = readDocText(doc);
        redisUtil.set(RedisKeys.DOC_TEXT + doc.getId(), loaded, docTextTtlHours, TimeUnit.HOURS);
        return loaded;
    }

    /* ============================ 私有方法 ============================ */

    /**
     * 校验文档存在 / 归属 / 解析就绪。
     */
    private Document requireReadyDocument(Long documentId, Long userId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!doc.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (doc.getStatus() == null || doc.getStatus() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    doc.getStatus() != null && doc.getStatus() == 2
                            ? "该文档解析失败，无法进行问答"
                            : "该文档正在解析中，请稍后刷新再试");
        }
        return doc;
    }

    /**
     * 查询并校验会话归属。
     */
    private ChatSession requireOwnedSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return session;
    }

    /**
     * 插入消息并累加会话 message_count。
     *
     * @param session 目标会话
     * @param role    角色（USER/ASSISTANT）
     * @param content 内容
     */
    private void insertMessage(ChatSession session, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(session.getId());
        msg.setRole(role);
        msg.setContent(content);
        chatMessageMapper.insert(msg);
        // 累加消息数（update_time 由数据库自动刷新，用于会话列表排序）
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        chatSessionMapper.updateById(session);
    }

    /**
     * 落库助手回复（含引用来源），返回消息 ID。
     *
     * @param sessionId 会话 ID
     * @param content   回答全文
     * @param sources   引用来源（随消息持久化，供历史回显）
     * @return 消息 ID
     */
    private Long insertAssistantMessage(Long sessionId, String content, List<SourceVO> sources) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        msg.setSources(writeSources(sources));
        chatMessageMapper.insert(msg);
        // 同步累加会话消息数
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
            chatSessionMapper.updateById(session);
        }
        return msg.getId();
    }

    /**
     * 读取文档文本文件（UTF-8）。文本文件由 Document 模块解析阶段生成。
     */
    private String readDocText(Document doc) {
        try {
            if (!StringUtils.hasText(doc.getTextPath())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "文档文本缺失，请重新上传");
            }
            return Files.readString(storagePathUtil.toAbsolute(doc.getTextPath()), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[问答] 读取文档文本失败, docId={}", doc.getId(), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "文档内容读取失败");
        }
    }

    /**
     * 加载最近多轮历史：Redis CHAT_CTX 缓存命中直接使用（滑动续期由写入方负责）；
     * 未命中回源 DB 最近 ≤CTX_KEEP 条并回填缓存 —— 减少对数据库的重复查询。
     */
    private List<Map<String, String>> loadHistory(Long sessionId) {
        List<Map<String, String>> history = castCtxList(
                redisUtil.getList(RedisKeys.CHAT_CTX + sessionId, Map.class));
        if (history != null) {
            return history;
        }
        // 回源：按 id 倒序取最近 CTX_KEEP 条，再转正序
        List<ChatMessage> recent = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + CTX_KEEP));
        history = new ArrayList<>(recent.size());
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage m = recent.get(i);
            history.add(Map.of("role", m.getRole().toLowerCase(), "content", m.getContent()));
        }
        redisUtil.set(RedisKeys.CHAT_CTX + sessionId, history, contextTtlMinutes, TimeUnit.MINUTES);
        return history;
    }

    /**
     * 问答结束后刷新上下文缓存：追加（用户问题 + 助手回复），仅保留最近 CTX_KEEP 条并续期。
     *
     * @param ctx    本轮上下文
     * @param answer 本轮助手回复全文
     */
    private void refreshContextCache(ChatContext ctx, String answer) {
        List<Map<String, String>> history = castCtxList(
                redisUtil.getList(RedisKeys.CHAT_CTX + ctx.getSessionId(), Map.class));
        if (history == null) {
            history = new ArrayList<>();
        }
        // 防御：prepare 阶段若已回填过（含本次 USER 消息前），此处只追加助手回复即可；
        // 为简单与正确，统一从当前缓存重建：剔除可能重复的尾部 USER 问题
        if (!history.isEmpty()) {
            Map<String, String> last = history.get(history.size() - 1);
            if ("user".equals(last.get("role")) && ctx.getQuestion().equals(last.get("content"))) {
                history.remove(history.size() - 1);
            }
        }
        history.add(Map.of("role", "user", "content", ctx.getQuestion()));
        history.add(Map.of("role", "assistant", "content", answer == null ? "" : answer));
        // 只保留最近 CTX_KEEP 条
        if (history.size() > CTX_KEEP) {
            history = new ArrayList<>(history.subList(history.size() - CTX_KEEP, history.size()));
        }
        redisUtil.set(RedisKeys.CHAT_CTX + ctx.getSessionId(), history,
                contextTtlMinutes, TimeUnit.MINUTES);
    }

    /**
     * 发送一个 SSE 事件（name=message，data=JSON）。
     */
    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(objectMapper.writeValueAsString(payload)));
    }

    /**
     * 静默发送 error 事件（发送失败仅日志，不二次抛出）。
     */
    private void sendEventQuietly(SseEmitter emitter, Map<String, Object> payload) {
        try {
            sendEvent(emitter, payload);
        } catch (Exception ignored) {
            log.debug("[问答] error 事件发送失败（客户端可能已断开）");
        }
    }

    /**
     * 检索命中片段 → 引用来源 VO。
     *
     * @param chunk 命中片段
     * @return 来源 VO
     */
    private SourceVO toSourceVO(RetrievedChunk chunk) {
        SourceVO vo = new SourceVO();
        vo.setRefIndex(chunk.getRefIndex());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setScore(chunk.getScore());
        vo.setSnippet(chunk.getContent());
        vo.setCharStart(chunk.getCharStart());
        vo.setCharEnd(chunk.getCharEnd());
        return vo;
    }

    /**
     * 引用来源列表 → JSON 字符串（落库用）；空列表返回 null，避免存 "[]" 占用空间。
     *
     * @param sources 引用来源
     * @return JSON 字符串或 null
     */
    private String writeSources(List<SourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            log.warn("[问答] 引用来源序列化失败，本条消息将不保存来源", e);
            return null;
        }
    }

    /**
     * 引用来源 JSON → 列表（历史消息回显用）。
     *
     * @param json chat_message.sources 列的值
     * @return 引用来源列表；解析失败返回 null
     */
    private List<SourceVO> readSources(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SourceVO>>() {
            });
        } catch (Exception e) {
            // 来源数据损坏不应导致整个历史列表拉取失败
            log.warn("[问答] 引用来源解析失败，该条消息不展示来源", e);
            return null;
        }
    }

    /**
     * 会话标题：取问题前 15 个字符（去空白）。
     */
    private String shortTitle(String question) {
        String q = question == null ? "" : question.trim().replaceAll("\\s+", " ");
        return q.length() > 15 ? q.substring(0, 15) : (q.isEmpty() ? "新对话" : q);
    }

    /**
     * 泛型安全转换：Map.class 原始类型反序列化后的类型擦除强转辅助。
     *
     * @param list 原始类型 List
     * @return 按 List&lt;Map&lt;String,String&gt;&gt; 使用的列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> castCtxList(List<?> list) {
        return (List<Map<String, String>>) (List<?>) list;
    }

    /**
     * 会话实体 → VO。
     */
    private SessionVO toSessionVO(ChatSession session, String documentName) {
        SessionVO vo = new SessionVO();
        BeanUtils.copyProperties(session, vo);
        vo.setDocumentName(documentName == null ? "" : documentName);
        return vo;
    }

    /**
     * 消息实体 → VO（含引用来源反序列化）。
     */
    private MessageVO toMessageVO(ChatMessage msg) {
        MessageVO vo = new MessageVO();
        BeanUtils.copyProperties(msg, vo);
        vo.setSources(readSources(msg.getSources()));
        return vo;
    }
}
