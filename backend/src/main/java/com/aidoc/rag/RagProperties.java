package com.aidoc.rag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索增强配置，对应 application.yml 的 aidoc.rag.* 段。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aidoc.rag")
public class RagProperties {

    /** 是否启用检索增强；关闭则回退为「整篇文档注入」的旧策略 */
    private boolean enabled = true;

    /** 文本切片目标字符数 */
    private int chunkSize = 500;

    /** 相邻切片重叠字符数（防止切点割裂语义） */
    private int chunkOverlap = 80;

    /** 召回片段数量上限 */
    private int topK = 4;

    /** 相似度阈值：低于该值的片段直接丢弃，宁缺毋滥 */
    private double scoreThreshold = 0.25D;

    /** 是否合并原文档中相邻的命中片段，减少上下文碎片 */
    private boolean mergeAdjacent = true;

    /** 送入模型的参考资料总字符上限（防止超出上下文窗口） */
    private int maxContextChars = 6000;

    /** 零召回时是否直接拒答（不调用大模型），用于抑制幻觉 */
    private boolean refuseWhenEmpty = true;
}
