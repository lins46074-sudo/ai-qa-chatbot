package com.aidoc.vo;

import lombok.Data;

/**
 * 看板趋势单日数据 VO（近 7 日逐日，含今日）。
 *
 * <p>date 形如 yyyy-MM-dd；缺失日期由 Java 端补 0 后返回。</p>
 */
@Data
public class TrendPointVO {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日接口访问量（access_log 行数） */
    private Long visitCount;

    /** 当日问答消息量（USER+ASSISTANT 消息数） */
    private Long messageCount;
}
