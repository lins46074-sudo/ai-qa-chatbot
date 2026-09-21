package com.aidoc.vo;

import lombok.Data;

/**
 * 引用来源 VO（随 SSE sources 事件下发，并随消息落库以便历史回显）。
 *
 * <p>前端在回答气泡下方以折叠面板展示该列表，用户可直接核对答案依据的原文片段与相似度，
 * 从而判断回答是否可信 —— 这是抑制幻觉的产品侧手段。</p>
 */
@Data
public class SourceVO {

    /** 引用编号（与答案正文中的 [n] 标注一一对应） */
    private int refIndex;

    /** 片段在原文档中的切片序号 */
    private int chunkIndex;

    /** 相似度得分（0~1，越高越相关） */
    private double score;

    /** 片段原文 */
    private String snippet;

    /** 片段在原文档规范化文本中的起始下标 */
    private int charStart;

    /** 片段在原文档规范化文本中的结束下标（不含） */
    private int charEnd;
}
