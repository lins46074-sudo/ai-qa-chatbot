package com.aidoc.service;

import com.aidoc.common.PageResult;
import com.aidoc.dto.MessageSendDTO;
import com.aidoc.dto.SessionCreateDTO;
import com.aidoc.vo.MessageVO;
import com.aidoc.vo.SessionVO;
import com.aidoc.vo.SourceVO;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 问答会话服务接口。
 *
 * <p>采用<b>两段式流式问答</b>设计：
 * <ol>
 *   <li>{@link #prepare}：在控制器创建 SseEmitter <b>之前</b>完成全部校验与落库
 *       （文档归属、自动建会话、保存用户消息、取文档文本、组装 LLM 上下文）。
 *       校验失败时抛 {@link com.aidoc.common.BusinessException}，由全局异常处理器返回 JSON ——
 *       避免"先开 SSE 再报错"导致前端只能收到半截流；</li>
 *   <li>{@link #streamAnswer}：拿到 emitter 后同步流式转发模型增量，全程异常转 SSE error 事件。</li>
 * </ol></p>
 */
public interface ChatService {

    /** SSE 事件分隔符常量（发送每个事件后追加空行） */
    String SSE_EVENT = "\n\n";

    /**
     * 会话分页（新建时间倒序，批量回填文档名避免 N+1）。
     *
     * @param userId  当前用户
     * @param current 页码
     * @param size    每页条数
     * @return 会话分页
     */
    PageResult<SessionVO> pageSessions(Long userId, long current, long size);

    /**
     * 新建会话（须归属校验文档）。
     *
     * @param dto    新建参数
     * @param userId 当前用户
     * @return 会话视图
     */
    SessionVO createSession(SessionCreateDTO dto, Long userId);

    /**
     * 重命名会话。
     *
     * @param sessionId 会话 ID
     * @param title     新标题
     * @param userId    当前用户
     * @return 更新后的会话视图
     */
    SessionVO renameSession(Long sessionId, String title, Long userId);

    /**
     * 删除会话（连同历史消息与 Redis 上下文缓存）。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户
     */
    void deleteSession(Long sessionId, Long userId);

    /**
     * 会话历史消息分页（按 id 倒序：current=1 为最新一页）。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户（归属校验）
     * @param current   页码
     * @param size      每页条数
     * @return 消息分页
     */
    PageResult<MessageVO> pageMessages(Long sessionId, Long userId, long current, long size);

    /**
     * 第一阶段：校验 + 落库 + 组装上下文（在创建 SseEmitter 前调用，可抛业务异常）。
     *
     * @param dto    提问参数
     * @param userId 当前用户
     * @return 已就绪的流式上下文
     */
    ChatContext prepare(MessageSendDTO dto, Long userId);

    /**
     * 第二阶段：把 LLM 增量同步转发到 SSE，结束后落库助手回复并刷新上下文缓存。
     * 本方法内部捕获一切异常转 SSE error 事件，绝不向上抛出。
     *
     * @param ctx     prepare 阶段产物
     * @param emitter 已创建的 SseEmitter
     */
    void streamAnswer(ChatContext ctx, SseEmitter emitter);

    /**
     * 流式问答上下文（prepare 与 streamAnswer 之间的载体）。
     */
    @Data
    class ChatContext {
        /** 当前用户 ID */
        private Long userId;
        /** 会话 ID（prepare 内可能自动新建） */
        private Long sessionId;
        /** 关联文档 ID */
        private Long documentId;
        /** 本次会话是否由 prepare 自动新建（决定是否先发 session 事件） */
        private boolean sessionCreated;
        /** 用户问题原文 */
        private String question;
        /** 发给 LLM 的完整消息序列（system + 历史 + 当前问题） */
        private List<Map<String, String>> messages;
        /** 本轮检索命中的引用来源（随 SSE sources 事件下发并落库；无命中时为空列表） */
        private List<SourceVO> sources;
        /** 是否因检索无命中而直接拒答（为 true 时不调用大模型，从源头杜绝编造） */
        private boolean noEvidence;
    }
}
