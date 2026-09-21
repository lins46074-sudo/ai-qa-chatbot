package com.aidoc.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据看板汇总 VO（GET /api/admin/overview）。
 */
@Data
public class StatsOverviewVO {

    /** 用户总数 */
    private Long userCount;

    /** 文档总数 */
    private Long documentCount;

    /** 会话总数 */
    private Long sessionCount;

    /** 消息总数 */
    private Long messageCount;

    /** 今日访问量（access_log 当日行数） */
    private Long todayVisitCount;

    /** 今日消息量 */
    private Long todayMessageCount;

    /** 今日新增用户数 */
    private Long todayNewUserCount;

    /** 近 7 日访问 / 消息趋势（含今日，缺失日期补 0） */
    private List<TrendPointVO> trend;

    /** 热门文档 Top10（按解析字符数降序） */
    private List<TopDocumentVO> topDocuments;
}
