package com.aidoc.service.impl;

import com.aidoc.common.BusinessException;
import com.aidoc.common.PageResult;
import com.aidoc.common.ResultCode;
import com.aidoc.dto.RegisterDTO;
import com.aidoc.dto.ResetPasswordDTO;
import com.aidoc.dto.UserStatusDTO;
import com.aidoc.entity.AccessLog;
import com.aidoc.entity.ChatMessage;
import com.aidoc.entity.ChatSession;
import com.aidoc.entity.Document;
import com.aidoc.entity.User;
import com.aidoc.mapper.AccessLogMapper;
import com.aidoc.mapper.ChatMessageMapper;
import com.aidoc.mapper.ChatSessionMapper;
import com.aidoc.mapper.DocumentMapper;
import com.aidoc.mapper.StatsMapper;
import com.aidoc.mapper.UserMapper;
import com.aidoc.rag.ChunkIndexService;
import com.aidoc.service.AdminService;
import com.aidoc.util.RedisKeys;
import com.aidoc.util.RedisUtil;
import com.aidoc.util.StoragePathUtil;
import com.aidoc.vo.AccessLogVO;
import com.aidoc.vo.AdminUserVO;
import com.aidoc.vo.DocumentVO;
import com.aidoc.vo.StatsOverviewVO;
import com.aidoc.vo.TopDocumentVO;
import com.aidoc.vo.TrendPointVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端服务实现：用户 / 文档 / 访问看板三大能力。
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final DocumentMapper documentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AccessLogMapper accessLogMapper;
    private final StatsMapper statsMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private final StoragePathUtil storagePathUtil;
    private final ChunkIndexService chunkIndexService;

    public AdminServiceImpl(UserMapper userMapper, DocumentMapper documentMapper,
                            ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper,
                            AccessLogMapper accessLogMapper, StatsMapper statsMapper,
                            PasswordEncoder passwordEncoder, RedisUtil redisUtil,
                            StoragePathUtil storagePathUtil, ChunkIndexService chunkIndexService) {
        this.userMapper = userMapper;
        this.documentMapper = documentMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.accessLogMapper = accessLogMapper;
        this.statsMapper = statsMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
        this.storagePathUtil = storagePathUtil;
        this.chunkIndexService = chunkIndexService;
    }

    /**
     * 用户分页：先分页查用户，再对当页用户做两次分组聚合回填
     * 文档数 / 会话数（避免 N+1 与全表子查询）。
     */
    @Override
    public PageResult<AdminUserVO> pageUsers(long current, long size, String keyword,
                                             String role, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword.trim())
                    .or().like(User::getNickname, keyword.trim()));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role.trim().toUpperCase());
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getId);
        Page<User> page = userMapper.selectPage(new Page<>(current, size), wrapper);

        List<AdminUserVO> records = new ArrayList<>();
        if (!page.getRecords().isEmpty()) {
            List<Long> userIds = page.getRecords().stream().map(User::getId).toList();
            // 一次 IN 查询分组统计文档数
            Map<Long, Long> docCountMap = groupCount(documentMapper.selectList(
                            new LambdaQueryWrapper<Document>().in(Document::getUserId, userIds)),
                    Document::getUserId);
            // 一次 IN 查询分组统计会话数
            Map<Long, Long> sessionCountMap = groupCount(chatSessionMapper.selectList(
                            new LambdaQueryWrapper<ChatSession>().in(ChatSession::getUserId, userIds)),
                    ChatSession::getUserId);
            for (User u : page.getRecords()) {
                AdminUserVO vo = new AdminUserVO();
                BeanUtils.copyProperties(u, vo);
                vo.setDocCount(docCountMap.getOrDefault(u.getId(), 0L).intValue());
                vo.setSessionCount(sessionCountMap.getOrDefault(u.getId(), 0L).intValue());
                records.add(vo);
            }
        }
        return PageResult.of(page, records);
    }

    /**
     * 新增用户：用户名查重 → BCrypt 加密 → 落库（默认角色 USER）。
     */
    @Override
    public void createUser(RegisterDTO dto) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail() == null ? "" : dto.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 启停用户：禁用时删除其 Redis 登录态 → 强制下线（拦截器比对登录态即 401）。
     */
    @Override
    public void updateStatus(Long userId, UserStatusDTO dto) {
        User user = requireUser(userId);
        user.setStatus(dto.getStatus());
        userMapper.updateById(user);
        if (dto.getStatus() != null && dto.getStatus() == 0) {
            redisUtil.delete(RedisKeys.LOGIN + userId);
            log.info("[用户管理] 禁用用户并强制下线: {}", user.getUsername());
        }
    }

    /**
     * 重置密码：BCrypt 加密后覆盖，同时删除旧登录态（旧 token 全部失效）。
     */
    @Override
    public void resetPassword(Long userId, ResetPasswordDTO dto) {
        User user = requireUser(userId);
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        redisUtil.delete(RedisKeys.LOGIN + userId);
    }

    /**
     * 删除用户：级联清理其全部资源。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        User user = requireUser(userId);
        // 1. 清理其全部文档（文件 + 缓存 + 会话 + 消息）
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>().eq(Document::getUserId, userId));
        for (Document doc : docs) {
            removeDocumentRes(doc);
        }
        // 2. 兜底：删除剩余无文档关联的会话（正常流程不会出现，防御性处理）
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getUserId, userId));
        if (!sessions.isEmpty()) {
            List<Long> sessionIds = sessions.stream().map(ChatSession::getId).toList();
            chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                    .in(ChatMessage::getSessionId, sessionIds));
            chatSessionMapper.deleteBatchIds(sessionIds);
            sessionIds.forEach(sid -> redisUtil.delete(RedisKeys.CHAT_CTX + sid));
        }
        // 3. 删除登录态与用户行
        redisUtil.delete(RedisKeys.LOGIN + userId);
        userMapper.deleteById(userId);
        log.info("[用户管理] 删除用户及其数据: {}", user.getUsername());
    }

    /**
     * 全库文档分页：分页查文档后按用户 ID 批量回填用户名。
     */
    @Override
    public PageResult<DocumentVO> pageDocs(long current, long size, String keyword) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Document::getFileName, keyword.trim());
        }
        wrapper.orderByDesc(Document::getId);
        Page<Document> page = documentMapper.selectPage(new Page<>(current, size), wrapper);

        List<DocumentVO> records = new ArrayList<>();
        if (!page.getRecords().isEmpty()) {
            List<Long> userIds = page.getRecords().stream().map(Document::getUserId).distinct().toList();
            Map<Long, String> usernames = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername));
            for (Document doc : page.getRecords()) {
                DocumentVO vo = new DocumentVO();
                BeanUtils.copyProperties(doc, vo);
                vo.setUserName(usernames.get(doc.getUserId()));
                records.add(vo);
            }
        }
        return PageResult.of(page, records);
    }

    /**
     * 强删任意文档（跨用户）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocForce(Long docId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        removeDocumentRes(doc);
    }

    /**
     * 看板汇总：总数 + 今日 + 近 7 日趋势（日期骨架补零）+ Top10 文档。
     */
    @Override
    public StatsOverviewVO overview() {
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setUserCount(safeLong(statsMapper.countUsers()));
        vo.setDocumentCount(safeLong(statsMapper.countDocuments()));
        vo.setSessionCount(safeLong(statsMapper.countSessions()));
        vo.setMessageCount(safeLong(statsMapper.countMessages()));
        vo.setTodayNewUserCount(safeLong(statsMapper.countTodayNewUsers()));
        vo.setTodayMessageCount(safeLong(statsMapper.countTodayMessages()));
        vo.setTodayVisitCount(safeLong(statsMapper.countTodayVisits()));

        // 近 7 日趋势：Java 端生成日期骨架，合并两次分组查询结果，缺失日补 0
        Map<String, Long> visitByDay = toDayCountMap(statsMapper.countVisitsByDay());
        Map<String, Long> messageByDay = toDayCountMap(statsMapper.countMessagesByDay());
        List<TrendPointVO> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).format(fmt);
            TrendPointVO point = new TrendPointVO();
            point.setDate(date);
            point.setVisitCount(visitByDay.getOrDefault(date, 0L));
            point.setMessageCount(messageByDay.getOrDefault(date, 0L));
            trend.add(point);
        }
        vo.setTrend(trend);

        // Top10 文档
        List<TopDocumentVO> topDocs = new ArrayList<>();
        for (Map<String, Object> row : statsMapper.selectTopDocuments()) {
            TopDocumentVO td = new TopDocumentVO();
            td.setDocumentId(toLong(row.get("document_id")));
            td.setFileName(str(row.get("file_name")));
            td.setUserName(str(row.get("user_name")));
            td.setFileSize(toLong(row.get("file_size")));
            td.setCharCount(toLong(row.get("char_count")).intValue());
            td.setCreateTime(toLocalDateTime(row.get("create_time")));
            topDocs.add(td);
        }
        vo.setTopDocuments(topDocs);
        return vo;
    }

    /**
     * 访问日志分页（倒序）。
     */
    @Override
    public PageResult<AccessLogVO> pageAccessLogs(long current, long size) {
        Page<AccessLog> page = accessLogMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<AccessLog>().orderByDesc(AccessLog::getId));
        List<AccessLogVO> records = page.getRecords().stream().map(a -> {
            AccessLogVO vo = new AccessLogVO();
            BeanUtils.copyProperties(a, vo);
            return vo;
        }).toList();
        return PageResult.of(page, records);
    }

    /* ============================ 私有方法 ============================ */

    /**
     * 删除单个文档的全部资源（磁盘文件、切片索引、Redis 缓存、其下会话与消息）。
     * 文档删除与用户删除共用，保证两条链路清理一致。
     */
    private void removeDocumentRes(Document doc) {
        // 1. 会话级联
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getDocumentId, doc.getId()));
        if (!sessions.isEmpty()) {
            List<Long> sessionIds = sessions.stream().map(ChatSession::getId).toList();
            chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                    .in(ChatMessage::getSessionId, sessionIds));
            chatSessionMapper.deleteBatchIds(sessionIds);
            sessionIds.forEach(sid -> redisUtil.delete(RedisKeys.CHAT_CTX + sid));
        }
        // 2. 磁盘文件（原文件 + 文本文件）与全文缓存
        deleteQuietly(storagePathUtil.originalAbs(doc.getUserId(), doc.getId(), doc.getFileType()));
        deleteQuietly(storagePathUtil.textAbs(doc.getUserId(), doc.getId()));
        redisUtil.delete(RedisKeys.DOC_TEXT + doc.getId());
        // 3. 切片索引（切片行 + 向量缓存）
        chunkIndexService.removeIndex(doc.getId());
        // 4. 文档行
        documentMapper.deleteById(doc.getId());
    }

    /**
     * 实体列表按 key 提取器分组计数。
     */
    private <T> Map<Long, Long> groupCount(List<T> list, Function<T, Long> keyExtractor) {
        return list.stream().collect(Collectors.groupingBy(keyExtractor, Collectors.counting()));
    }

    /**
     * 取用户实体（不存在抛 404）。
     */
    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return user;
    }

    /**
     * 聚合行（date/cnt）转 Map&lt;date, count&gt;。
     */
    private Map<String, Long> toDayCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object date = row.get("date");
            if (date != null) {
                map.put(String.valueOf(date), toLong(row.get("cnt")));
            }
        }
        return map;
    }

    /** Long 安全转换（MySQL COUNT 返回 Long，聚合可能为 null） */
    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    /** Object → Long */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    /** Object → String */
    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Object → LocalDateTime（MyBatis 聚合查询日期通常返回 java.sql.Timestamp）。
     */
    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt;
        }
        return java.time.LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    /**
     * 静默删除文件/目录。
     */
    private void deleteQuietly(java.nio.file.Path path) {
        try {
            if (path == null || !java.nio.file.Files.exists(path)) {
                return;
            }
            if (java.nio.file.Files.isDirectory(path)) {
                try (var walk = java.nio.file.Files.walk(path)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    java.nio.file.Files.deleteIfExists(p);
                                } catch (Exception ignored) {
                                    // 忽略单文件删除失败
                                }
                            });
                }
            } else {
                java.nio.file.Files.deleteIfExists(path);
            }
        } catch (Exception e) {
            log.warn("[管理端] 文件删除失败: {}", path, e);
        }
    }
}
