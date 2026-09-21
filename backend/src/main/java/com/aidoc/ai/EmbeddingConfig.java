package com.aidoc.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 向量化模型装配：依据是否配置了 Embedding API Key 选择具体实现。
 *
 * <p>选择策略：
 * <ul>
 *   <li>配置了 {@code aidoc.llm.embedding.api-key} → {@link ApiEmbeddingModel}（语义向量，召回质量高）；</li>
 *   <li>未配置 → {@link LocalEmbeddingModel}（本地哈希向量，零外部依赖）。</li>
 * </ul>
 * 这样设计的好处是：项目在<b>没有任何外部向量服务</b>的机器上也能完整跑通 RAG 链路，
 * 面试演示不会因为缺 Key 而失败。</p>
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    /**
     * 装配向量化模型 Bean。
     *
     * @param properties Embedding 配置
     * @return 语义向量模型或本地兜底模型
     */
    @Bean
    public EmbeddingModel embeddingModel(EmbeddingProperties properties) {
        if (StringUtils.hasText(properties.getApiKey())) {
            log.info("[Embedding] 使用语义向量模型: model={}, baseUrl={}",
                    properties.getModel(), properties.getBaseUrl());
            return new ApiEmbeddingModel(properties);
        }
        log.info("[Embedding] 未配置 Embedding API Key，降级为本地哈希向量模型: dimension={}",
                properties.getDimension());
        return new LocalEmbeddingModel(properties.getDimension());
    }
}
