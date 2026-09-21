package com.aidoc.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 管理端统计 Mapper：复杂聚合 SQL（计数 / 按日分组 / 联查 Top10）。
 *
 * <p>说明：所有方法均为只读聚合查询，SQL 直接以注解方式维护，避免 XML 配置。
 * 返回 List&lt;Map&lt;String,Object&gt;&gt; 的列别名即 Map 的 key（小写下划线），
 * 由 AdminService 负责转换为 VO。</p>
 */
@Mapper
public interface StatsMapper {

    /** 用户总数 */
    @Select("SELECT COUNT(*) FROM sys_user")
    Long countUsers();

    /** 文档总数 */
    @Select("SELECT COUNT(*) FROM doc_document")
    Long countDocuments();

    /** 会话总数 */
    @Select("SELECT COUNT(*) FROM chat_session")
    Long countSessions();

    /** 消息总数 */
    @Select("SELECT COUNT(*) FROM chat_message")
    Long countMessages();

    /** 今日新增用户数 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE create_time >= CURDATE()")
    Long countTodayNewUsers();

    /** 今日新增消息数（问答活跃度） */
    @Select("SELECT COUNT(*) FROM chat_message WHERE create_time >= CURDATE()")
    Long countTodayMessages();

    /** 今日访问量（access_log 当日行数） */
    @Select("SELECT COUNT(*) FROM access_log WHERE create_time >= CURDATE()")
    Long countTodayVisits();

    /**
     * 近 7 日访问量按日分组（date 为 yyyy-MM-dd）。
     */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS cnt " +
            "FROM access_log WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(create_time)")
    List<Map<String, Object>> countVisitsByDay();

    /**
     * 近 7 日问答消息量按日分组（date 为 yyyy-MM-dd）。
     */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS cnt " +
            "FROM chat_message WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(create_time)")
    List<Map<String, Object>> countMessagesByDay();

    /**
     * Top10 大文档：按解析字符数降序，联查上传人用户名。
     */
    @Select("SELECT d.id AS document_id, d.file_name, d.file_size, d.char_count, " +
            "d.create_time, u.username AS user_name " +
            "FROM doc_document d LEFT JOIN sys_user u ON d.user_id = u.id " +
            "ORDER BY d.char_count DESC LIMIT 10")
    List<Map<String, Object>> selectTopDocuments();
}
