package com.aidoc.service.impl;

import com.aidoc.common.BusinessException;
import com.aidoc.common.PageResult;
import com.aidoc.common.ResultCode;
import com.aidoc.dto.ChunkMergeDTO;
import com.aidoc.entity.ChatMessage;
import com.aidoc.entity.ChatSession;
import com.aidoc.entity.Document;
import com.aidoc.mapper.ChatMessageMapper;
import com.aidoc.mapper.ChatSessionMapper;
import com.aidoc.mapper.DocumentMapper;
import com.aidoc.parser.PdfTextParser;
import com.aidoc.parser.TxtTextParser;
import com.aidoc.rag.ChunkIndexService;
import com.aidoc.service.DocumentService;
import com.aidoc.util.RedisKeys;
import com.aidoc.util.RedisUtil;
import com.aidoc.util.StoragePathUtil;
import com.aidoc.vo.ChunkStatusVO;
import com.aidoc.vo.DocumentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 文档服务实现。
 *
 * <p>上传链路：<b>直传 / 分片（校验 MD5）→ 保存原文件 → PDF/TXT 解析 → 写文本文件 →
 * 回填元数据（页数/字符数/状态）→ 建立切片向量索引 → 全文缓存到 Redis</b>。
 * 删除时级联清理会话、消息、切片索引与 Redis 缓存键，避免脏数据。</p>
 *
 * <p>解析成功必须同时建索引成功，文档才置为「就绪」：只写文本不建切片的话，
 * 问答时会检索不到任何片段，用户看到的是「文档中未找到相关信息」这种难以排查的现象，
 * 因此这里把建索引失败也按解析失败处理，并给出明确原因。</p>
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RedisUtil redisUtil;
    private final StoragePathUtil storagePathUtil;
    private final PdfTextParser pdfTextParser;
    private final TxtTextParser txtTextParser;
    private final ChunkIndexService chunkIndexService;

    /** 允许的最大文档大小（MB，来自 aidoc.upload.max-file-mb） */
    @Value("${aidoc.upload.max-file-mb}")
    private long maxFileMb;

    /** 文档全文 Redis 缓存 TTL（小时，来自 aidoc.redis-ttl.doc-text-hours） */
    @Value("${aidoc.redis-ttl.doc-text-hours}")
    private long docTextTtlHours;

    public DocumentServiceImpl(DocumentMapper documentMapper, ChatSessionMapper chatSessionMapper,
                               ChatMessageMapper chatMessageMapper, RedisUtil redisUtil,
                               StoragePathUtil storagePathUtil, PdfTextParser pdfTextParser,
                               TxtTextParser txtTextParser, ChunkIndexService chunkIndexService) {
        this.documentMapper = documentMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.redisUtil = redisUtil;
        this.storagePathUtil = storagePathUtil;
        this.pdfTextParser = pdfTextParser;
        this.txtTextParser = txtTextParser;
        this.chunkIndexService = chunkIndexService;
    }

    /**
     * 小文件直接上传：建记录 → 存原文件 → 同步解析。
     */
    @Override
    public DocumentVO upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "上传文件不能为空");
        }
        String type = validateExt(file.getOriginalFilename());
        checkFileSize(file.getSize());

        // 1. 先建记录拿 docId（文件命名依赖 docId）
        Document doc = createRow(userId, file.getOriginalFilename(), type, file.getSize());
        try {
            // 2. 保存原文件到 docs/{userId}/orig/{docId}.{ext}
            Path dest = storagePathUtil.originalAbs(userId, doc.getId(), type);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            doc.setOriginalPath(toRelative(dest));
            documentMapper.updateById(doc);
            // 3. 同步解析文本
            parseAndSave(doc);
        } catch (Exception e) {
            // 落盘异常按解析失败处理
            markFailed(doc, "文件保存失败: " + e.getMessage());
            log.error("[文档上传] 保存原文件失败, userId={}", userId, e);
        }
        return toDocumentVO(documentMapper.selectById(doc.getId()));
    }

    /**
     * 查询某分片任务已上传的序号集合（断点续传）。
     */
    @Override
    public ChunkStatusVO chunkStatus(Long userId, String identifier) {
        ChunkStatusVO vo = new ChunkStatusVO();
        vo.setIdentifier(identifier);
        List<Integer> uploaded = new ArrayList<>();
        Path chunkDir = storagePathUtil.chunkDir(identifier);
        if (Files.isDirectory(chunkDir)) {
            try (var stream = Files.list(chunkDir)) {
                uploaded = stream.filter(p -> p.getFileName().toString().endsWith(".part"))
                        .map(p -> {
                            String name = p.getFileName().toString();
                            return Integer.parseInt(name.substring(0, name.indexOf('.')));
                        })
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("[分片] 查询已上传分片失败, identifier={}", identifier, e);
            }
        }
        vo.setUploaded(uploaded);
        return vo;
    }

    /**
     * 上传单个分片：直接落盘 {index}.part；文件已存在视为已上传（幂等）。
     */
    @Override
    public void uploadChunk(Long userId, MultipartFile file, String identifier, int index, int totalChunks) {
        // 1. 参数合法性
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "分片文件不能为空");
        }
        if (!StringUtils.hasText(identifier) || identifier.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "分片标识不合法");
        }
        if (index < 0 || totalChunks <= 0 || index >= totalChunks) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "分片序号或总数不合法");
        }
        try {
            Path target = storagePathUtil.chunkFile(identifier, index);
            // 2. 已存在则跳过（网络重试 / 断点续传场景）
            if (Files.exists(target)) {
                return;
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("[分片] 分片写入失败, identifier={}, index={}", identifier, index, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "分片写入失败，请重试");
        }
    }

    /**
     * 合并分片：完整性 → MD5 → 落位原文件 → 解析。
     */
    @Override
    public DocumentVO mergeChunks(Long userId, ChunkMergeDTO dto) {
        String type = validateExt(dto.getFileName());
        checkFileSize(dto.getFileSize());
        String identifier = dto.getIdentifier();

        // 1. 完整性校验：0..totalChunks-1 全部分片必须存在
        for (int i = 0; i < dto.getTotalChunks(); i++) {
            if (!Files.exists(storagePathUtil.chunkFile(identifier, i))) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "分片不完整，缺少第 " + (i + 1) + " 片，请重新上传缺失分片");
            }
        }

        // 2. 顺序合并并同步计算 MD5（边写边算，避免二次读盘）
        Path tmp = storagePathUtil.mergedTmp(identifier);
        try {
            String mergedMd5;
            try (OutputStream out = Files.newOutputStream(tmp)) {
                MessageDigest digest = MessageDigest.getInstance("MD5");
                byte[] buf = new byte[8192];
                for (int i = 0; i < dto.getTotalChunks(); i++) {
                    try (InputStream in = Files.newInputStream(storagePathUtil.chunkFile(identifier, i))) {
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            digest.update(buf, 0, len);
                            out.write(buf, 0, len);
                        }
                    }
                }
                mergedMd5 = toHex(digest.digest());
            }
            // 3. MD5 一致性校验（防传输损坏 / 防串文件）
            if (!mergedMd5.equalsIgnoreCase(identifier)) {
                deleteQuietly(storagePathUtil.chunkDir(identifier));
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "文件校验失败（MD5 不一致），请重新上传");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[分片合并] 合并失败, identifier={}", identifier, e);
            deleteQuietly(storagePathUtil.chunkDir(identifier));
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "合并失败，请重试");
        }

        // 4. 建记录并移动为最终原文件
        Document doc = createRow(userId, dto.getFileName(), type, dto.getFileSize());
        try {
            Path dest = storagePathUtil.originalAbs(userId, doc.getId(), type);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            doc.setOriginalPath(toRelative(dest));
            documentMapper.updateById(doc);
            // 5. 删除分片临时目录并解析
            deleteQuietly(storagePathUtil.chunkDir(identifier));
            parseAndSave(doc);
        } catch (Exception e) {
            markFailed(doc, "文件落位失败: " + e.getMessage());
            log.error("[分片合并] 落位原文件失败, userId={}, docId={}", userId, doc.getId(), e);
        }
        return toDocumentVO(documentMapper.selectById(doc.getId()));
    }

    /**
     * 我的文档分页：组合过滤条件（LambdaQueryWrapper 动态拼接，避免 SQL 注入）。
     */
    @Override
    public PageResult<DocumentVO> pageDocs(Long userId, long current, long size,
                                           String keyword, String fileType, Integer status) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getUserId, userId);
        // 关键字：文件名模糊匹配
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Document::getFileName, keyword.trim());
        }
        // 类型 / 状态筛选（空值不参与条件）
        if (StringUtils.hasText(fileType)) {
            wrapper.eq(Document::getFileType, fileType.trim().toUpperCase());
        }
        if (status != null) {
            wrapper.eq(Document::getStatus, status);
        }
        wrapper.orderByDesc(Document::getId);

        Page<Document> page = documentMapper.selectPage(new Page<>(current, size), wrapper);
        List<DocumentVO> records = page.getRecords().stream().map(this::toDocumentVO).toList();
        return PageResult.of(page, records);
    }

    /**
     * 文档详情：归属校验（只能查看本人文档）。
     */
    @Override
    public DocumentVO getDoc(Long userId, Long docId) {
        return toDocumentVO(requireOwned(userId, docId));
    }

    /**
     * 删除文档：级联删除会话/消息/文件/缓存。
     */
    @Override
    public void deleteDoc(Long userId, Long docId) {
        Document doc = requireOwned(userId, docId);
        // 1. 级联：先查该文档下全部会话 → 删消息 → 删会话 → 清各会话上下文缓存
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getDocumentId, docId));
        if (!sessions.isEmpty()) {
            List<Long> sessionIds = sessions.stream().map(ChatSession::getId).toList();
            chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>().in(ChatMessage::getSessionId, sessionIds));
            chatSessionMapper.deleteBatchIds(sessionIds);
            sessionIds.forEach(sid -> redisUtil.delete(RedisKeys.CHAT_CTX + sid));
        }
        // 2. 删除文件（原文件 + 文本文件）
        deleteQuietly(storagePathUtil.originalAbs(userId, docId, doc.getFileType()));
        deleteQuietly(storagePathUtil.textAbs(userId, docId));
        // 3. 删除切片索引（切片行 + 向量缓存）
        chunkIndexService.removeIndex(docId);
        // 4. 删除文档全文缓存与数据库记录
        redisUtil.delete(RedisKeys.DOC_TEXT + docId);
        documentMapper.deleteById(docId);
        log.info("[文档删除] userId={}, docId={}, fileName={}", userId, docId, doc.getFileName());
    }

    /* ============================ 私有方法 ============================ */

    /**
     * 解析文档并回填元数据：成功 status=1 并缓存全文；失败 status=2 + failReason。
     *
     * @param doc 文档（须已写入 original_path）
     */
    private void parseAndSave(Document doc) {
        String text = "";
        int pageCount = 0;
        try {
            Path orig = storagePathUtil.toAbsolute(doc.getOriginalPath());
            if ("PDF".equalsIgnoreCase(doc.getFileType())) {
                PdfTextParser.ParseResult result = pdfTextParser.parse(orig);
                pageCount = result.getPageCount();
                text = result.getText();
            } else {
                text = txtTextParser.parse(orig);
            }
            if (text == null) {
                text = "";
            }
            // 空内容判定：扫描件 PDF 或空文件给出可读提示
            if (text.trim().isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "PDF".equalsIgnoreCase(doc.getFileType()) && pageCount > 0
                                ? "PDF 无可提取文本层（疑似扫描件），暂不支持 OCR"
                                : "文档内容为空，无法解析");
            }
            // 写 UTF-8 文本文件
            Path textPath = storagePathUtil.textAbs(doc.getUserId(), doc.getId());
            Files.writeString(textPath, text, StandardCharsets.UTF_8);
            doc.setTextPath(toRelative(textPath));
            doc.setPageCount(pageCount);
            doc.setCharCount(text.length());
            // 解析成功 → 建立切片向量索引（RAG 检索的数据来源）
            int chunkCount = chunkIndexService.index(doc.getId(), text);
            if (chunkCount == 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "文档切片结果为空，无法建立检索索引");
            }
            doc.setStatus(1);
            doc.setFailReason("");
            // 全文缓存到 Redis：仅「全文注入」降级模式会读取（aidoc.rag.enabled=false）
            redisUtil.set(RedisKeys.DOC_TEXT + doc.getId(), text, docTextTtlHours, TimeUnit.HOURS);
        } catch (BusinessException e) {
            markFailed(doc, e.getMessage());
        } catch (Exception e) {
            markFailed(doc, "解析异常: " + e.getMessage());
            log.error("[文档解析] 失败, docId={}, fileName={}", doc.getId(), doc.getFileName(), e);
        } finally {
            documentMapper.updateById(doc);
        }
    }

    /**
     * 将文档置为解析失败状态（failReason 截断 200 字防超列宽）。
     *
     * @param doc    文档
     * @param reason 失败原因
     */
    private void markFailed(Document doc, String reason) {
        doc.setStatus(2);
        String safe = reason == null ? "" : reason;
        doc.setFailReason(safe.length() > 200 ? safe.substring(0, 200) : safe);
    }

    /**
     * 创建文档记录（status=0 解析中）。
     *
     * @param userId   所属用户
     * @param fileName 文件名
     * @param type     类型 PDF/TXT
     * @param size     字节数
     * @return 已落库文档（id 已回填）
     */
    private Document createRow(Long userId, String fileName, String type, Long size) {
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFileName(fileName);
        doc.setFileType(type.toUpperCase());
        doc.setFileSize(size);
        doc.setStatus(0);
        documentMapper.insert(doc);
        return doc;
    }

    /**
     * 校验并取出扩展名（仅允许 pdf / txt）。
     *
     * @param fileName 原始文件名
     * @return 大写扩展名
     */
    private String validateExt(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "文件名不能为空");
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅支持 PDF / TXT 文档");
        }
        String ext = fileName.substring(dot + 1);
        if (!"pdf".equalsIgnoreCase(ext) && !"txt".equalsIgnoreCase(ext)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅支持 PDF / TXT 文档");
        }
        return ext.toUpperCase();
    }

    /**
     * 校验文件大小上限。
     *
     * @param size 字节数
     */
    private void checkFileSize(Long size) {
        if (size == null || size <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "文件内容为空");
        }
        long maxBytes = maxFileMb * 1024L * 1024L;
        if (size > maxBytes) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "文件超过大小上限 " + maxFileMb + "MB，请压缩后重试");
        }
    }

    /**
     * 查询并校验文档归属（不存在→404，非本人→403）。
     *
     * @param userId 当前用户
     * @param docId  文档 ID
     * @return 文档实体
     */
    private Document requireOwned(Long userId, Long docId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!doc.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return doc;
    }

    /**
     * 绝对路径转库内相对路径（统一正斜杠分隔）。
     *
     * @param abs 绝对路径
     * @return 相对路径字符串
     */
    private String toRelative(Path abs) {
        return storagePathUtil.baseDir().relativize(abs).toString().replace('\\', '/');
    }

    /**
     * 字节数组转小写十六进制。
     *
     * @param bytes 摘要字节
     * @return hex 字符串
     */
    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 静默删除文件/目录（不存在或失败不报错，仅日志）。
     *
     * @param path 目标路径
     */
    private void deleteQuietly(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return;
            }
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 忽略单文件删除失败
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (Exception e) {
            log.warn("[文件清理] 删除失败: {}", path, e);
        }
    }

    /**
     * 实体 → 视图（userName 由管理端联查回填，本列表不填充）。
     *
     * @param doc 文档实体
     * @return 文档视图
     */
    private DocumentVO toDocumentVO(Document doc) {
        if (doc == null) {
            return null;
        }
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }
}
