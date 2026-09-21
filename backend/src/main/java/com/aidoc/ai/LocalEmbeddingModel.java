package com.aidoc.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地哈希向量模型（零外部依赖的兜底实现）。
 *
 * <p>把文本映射为固定维度的稀疏稠密向量，<b>不调用任何外部服务</b>，
 * 因此在未配置 Embedding API Key 时仍可跑通完整的「切片 → 向量 → 检索」链路，
 * 保证项目 clone 下来开箱即用。</p>
 *
 * <p>向量化流程：
 * <ol>
 *   <li><b>分词</b>：中文按<b>字符二元组</b>切分（无需引入分词器即可捕捉「文档」「问答」这类词序信息），
 *       英文与数字按连续段整体作为一个词；</li>
 *   <li><b>哈希映射</b>：词元哈希取模映射到固定维度，即 Hashing Trick ——
 *       无需维护词表，且不受新词影响；</li>
 *   <li><b>亚线性词频</b>：权重取 {@code 1 + ln(tf)}，抑制高频词对相似度的主导；</li>
 *   <li><b>L2 归一化</b>：使余弦相似度退化为向量点积，检索时省去一次开方。</li>
 * </ol></p>
 *
 * <p><b>无状态</b>：同一段文本任何时候都产出同一向量，故切片向量与问题向量天然处于同一空间
 * （接口 {@link EmbeddingModel} 对此有强制约定）。</p>
 *
 * <p>局限：这是词形匹配而非语义匹配，无法识别同义词（「汽车」与「轿车」不相似）；
 * 生产环境建议配置 {@link ApiEmbeddingModel} 使用语义向量。</p>
 */
public class LocalEmbeddingModel implements EmbeddingModel {

    /** 向量维度（哈希空间大小） */
    private final int dimension;

    /**
     * 构造本地向量模型。
     *
     * @param dimension 向量维度，非正数时回退为 512
     */
    public LocalEmbeddingModel(int dimension) {
        this.dimension = dimension > 0 ? dimension : 512;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String name() {
        return "local-hash-" + dimension;
    }

    @Override
    public List<double[]> embedAll(List<String> texts) {
        List<double[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(vectorize(text));
        }
        return result;
    }

    /**
     * 单段文本 → L2 归一化向量。
     *
     * @param text 原文
     * @return 维度为 {@link #dimension()} 的向量；文本为空时返回零向量
     */
    private double[] vectorize(String text) {
        double[] vector = new double[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }
        // 1. 统计各哈希桶的词频
        Map<Integer, Integer> termFreq = new HashMap<>();
        for (String token : tokenize(text)) {
            int bucket = Math.floorMod(token.hashCode(), dimension);
            termFreq.merge(bucket, 1, Integer::sum);
        }
        // 2. 亚线性词频加权，并同步累加平方和用于归一化
        double squareSum = 0D;
        for (Map.Entry<Integer, Integer> entry : termFreq.entrySet()) {
            double weight = 1D + Math.log(entry.getValue());
            vector[entry.getKey()] = weight;
            squareSum += weight * weight;
        }
        // 3. L2 归一化
        double norm = Math.sqrt(squareSum);
        if (norm > 0D) {
            for (int i = 0; i < dimension; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    /**
     * 轻量分词：中文取字符二元组，英文/数字取连续段。
     *
     * <p>用二元组而非单字，是因为单字区分度太低（「的」「了」到处出现），
     * 二元组能在不引入分词器的前提下近似还原词的边界信息。</p>
     *
     * @param text 原文
     * @return 词元列表
     */
    private List<String> tokenize(String text) {
        String lower = text.toLowerCase();
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char prevCjk = 0;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                // 进入中文：先结算已积累的英文/数字段
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
                if (prevCjk != 0) {
                    tokens.add(new String(new char[]{prevCjk, c}));
                }
                prevCjk = c;
            } else if (Character.isLetterOrDigit(c)) {
                prevCjk = 0;
                word.append(c);
            } else {
                // 标点/空白作为分隔符
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
                prevCjk = 0;
            }
        }
        if (word.length() > 0) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    /**
     * 是否 CJK 统一表意文字（覆盖常用汉字区间）。
     *
     * @param c 字符
     * @return 是汉字返回 true
     */
    private boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
