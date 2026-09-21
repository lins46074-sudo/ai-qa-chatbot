package com.aidoc.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量运算工具（final 工具类，不可实例化）。
 *
 * <p>提供相似度计算与向量的 JSON 编解码：切片向量以 JSON 数组字符串形式
 * 存放在 doc_chunk.embedding 列中，读取时需要还原为 double[]。</p>
 */
@Slf4j
public final class VectorMath {

    /** JSON 编解码器（线程安全，静态复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 私有构造器：工具类禁止实例化。
     */
    private VectorMath() {
    }

    /**
     * 余弦相似度，取值区间 [-1, 1]（本项目向量均非负，实际落在 [0, 1]）。
     *
     * <p>向量长度不一致、为空或存在零向量时返回 0，由调用方按「不相似」处理。</p>
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 余弦相似度；不可比较时返回 0
     */
    public static double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0D;
        }
        double dot = 0D;
        double normA = 0D;
        double normB = 0D;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        // 任一方为零向量时夹角无定义，直接判为不相似
        if (normA <= 0D || normB <= 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 向量序列化为 JSON 数组字符串（用于落库）。
     *
     * @param vector 向量
     * @return JSON 字符串
     */
    public static String toJson(double[] vector) {
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (Exception e) {
            log.error("[向量] 序列化失败", e);
            return "[]";
        }
    }

    /**
     * JSON 数组字符串还原为向量。
     *
     * @param json doc_chunk.embedding 列的值
     * @return 向量；解析失败或为空时返回长度为 0 的数组（由调用方按不可比处理）
     */
    public static double[] fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new double[0];
        }
        try {
            return OBJECT_MAPPER.readValue(json, double[].class);
        } catch (Exception e) {
            // 向量损坏不应中断整次检索，仅记录并让该切片因不可比而被过滤掉
            log.warn("[向量] 反序列化失败，该切片将不参与相似度计算");
            return new double[0];
        }
    }

    /**
     * 批量解码切片向量（一次遍历，避免逐个 try/catch 的开销）。
     *
     * @param jsonList JSON 字符串列表
     * @return 与入参一一对应的向量列表
     */
    public static List<double[]> fromJsonList(List<String> jsonList) {
        List<double[]> vectors = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            vectors.add(fromJson(json));
        }
        return vectors;
    }
}
