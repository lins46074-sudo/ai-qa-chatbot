package com.aidoc.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量化（Embedding）配置，对应 application.yml 的 aidoc.llm.embedding.* 段。
 *
 * <p><b>降级约定</b>：当 {@link #apiKey} 为空时，系统自动改用
 * {@link LocalEmbeddingModel} 本地哈希向量兜底，因此本项目<b>无需任何外部
 * Embedding 服务即可跑通完整 RAG 链路</b>；配置了 Key 则切换为语义向量，召回质量更高。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aidoc.llm.embedding")
public class EmbeddingProperties {

    /** 接口基址（OpenAI 兼容 /embeddings，不含后缀），为空时不起作用 */
    private String baseUrl;

    /** API Key，留空则降级为本地向量模型 */
    private String apiKey;

    /** 向量模型名（如 text-embedding-v3 / embedding-3 / text-embedding-3-small） */
    private String model = "text-embedding-v3";

    /** 向量维度：本地模型使用该值；API 模式下以服务端返回实际维度为准 */
    private int dimension = 512;

    /** 建立连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 读取响应超时（毫秒） */
    private int readTimeoutMs = 60000;

    /** 单次请求最多提交的切片数（避免请求体过大被服务端拒绝） */
    private int batchSize = 16;
}
