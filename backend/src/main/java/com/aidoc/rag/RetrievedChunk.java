package com.aidoc.rag;

import lombok.Data;

/**
 * 检索命中的片段（召回 + 过滤 + 合并之后的最终结果）。
 *
 * <p>{@link #refIndex} 是注入提示词时使用的引用编号，
 * 大模型被要求以 {@code [1]} {@code [2]} 的形式标注答案依据，前端据此把答案与原文片段对应起来，
 * 实现<b>引用溯源</b>。</p>
 */
@Data
public class RetrievedChunk {

    /** 引用编号（1 起，注入提示词与前端展示共用同一编号） */
    private int refIndex;

    /** 命中的切片在原文档中的序号（合并后取最小序号） */
    private int chunkIndex;

    /** 片段正文（合并后的内容） */
    private String content;

    /** 相似度得分（合并时取组内最高分） */
    private double score;

    /** 在原文档规范化文本中的起始下标 */
    private int charStart;

    /** 在原文档规范化文本中的结束下标（不含） */
    private int charEnd;

    /** 片段字符数（用于上下文预算裁剪） */
    public int length() {
        return content == null ? 0 : content.length();
    }
}
