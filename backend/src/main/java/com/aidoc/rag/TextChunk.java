package com.aidoc.rag;

import lombok.Data;

/**
 * 文本切片（切分阶段的产物，尚未向量化）。
 */
@Data
public class TextChunk {

    /** 在本篇文档内的切片序号（0 起，连续递增） */
    private int index;

    /** 切片正文 */
    private String content;

    /** 切片在<b>规范化文本</b>中的起始下标（用于溯源定位） */
    private int charStart;

    /** 切片在规范化文本中的结束下标（不含） */
    private int charEnd;
}
