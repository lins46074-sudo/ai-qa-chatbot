package com.aidoc.ai;

import java.util.List;

/**
 * 向量化模型抽象。
 *
 * <p>把「文本 → 向量」的能力抽象为接口，使检索模块与具体实现解耦：
 * <ul>
 *   <li>{@link ApiEmbeddingModel}：对接 OpenAI 兼容 /embeddings 接口，语义向量；</li>
 *   <li>{@link LocalEmbeddingModel}：本地哈希向量，零外部依赖的兜底实现。</li>
 * </ul>
 * 由 {@link EmbeddingConfig} 依据是否配置了 API Key 自动选择实现。</p>
 *
 * <p><b>实现约束</b>：切片向量与问题向量必须处于<b>同一向量空间</b>，
 * 因此实现必须是无状态的（对同一段文本，任何时候都产出同一向量）。
 * 若引入依赖语料统计量的算法（如需要 IDF 的 TF-IDF），须把统计量一并持久化，
 * 否则问题向量与切片向量不在同一空间，相似度将失去意义。</p>
 */
public interface EmbeddingModel {

    /**
     * 向量维度。
     *
     * @return 维度（本地模型由配置决定；API 模型首次调用后为服务端实际维度）
     */
    int dimension();

    /**
     * 模型标识（用于日志与前端展示，如 {@code api:text-embedding-v3}）。
     *
     * @return 可读的模型名
     */
    String name();

    /**
     * 批量向量化。
     *
     * @param texts 待向量化文本列表
     * @return 与入参一一对应的向量列表；入参为空时返回空列表
     */
    List<double[]> embedAll(List<String> texts);

    /**
     * 单条文本向量化（默认复用批量实现）。
     *
     * @param text 待向量化文本
     * @return 向量；文本为空时返回零向量
     */
    default double[] embed(String text) {
        List<double[]> vectors = embedAll(List.of(text == null ? "" : text));
        return vectors.isEmpty() ? new double[dimension()] : vectors.get(0);
    }
}
