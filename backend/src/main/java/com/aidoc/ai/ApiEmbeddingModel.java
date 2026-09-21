package com.aidoc.ai;

import com.aidoc.common.BusinessException;
import com.aidoc.common.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义向量模型：调用 OpenAI 兼容的 {@code POST /embeddings} 接口。
 *
 * <p>适用于通义 text-embedding-v3、智谱 embedding-3、OpenAI text-embedding-3-small 等
 * 任何遵循 OpenAI Embedding 协议的服务。</p>
 *
 * <p>请求：{@code {"model":"...","input":["文本1","文本2"]}}<br>
 * 响应：{@code {"data":[{"index":0,"embedding":[...]}, ...]}}</p>
 *
 * <p>注意响应 data 数组<b>不保证与入参顺序一致</b>，因此必须按 index 字段回填，
 * 否则切片内容与向量将发生错位。</p>
 */
@Slf4j
public class ApiEmbeddingModel implements EmbeddingModel {

    /** 配置项 */
    private final EmbeddingProperties properties;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 实际向量维度：首次调用后由服务端返回值确定 */
    private volatile int dimension;

    /**
     * 构造语义向量模型。
     *
     * @param properties Embedding 配置（须已配置 baseUrl 与 apiKey）
     */
    public ApiEmbeddingModel(EmbeddingProperties properties) {
        this.properties = properties;
        this.dimension = properties.getDimension();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String name() {
        return "api:" + properties.getModel();
    }

    @Override
    public List<double[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<double[]> result = new ArrayList<>(texts.size());
        // 分批提交，避免单次请求体过大被服务端拒绝
        int batchSize = Math.max(1, properties.getBatchSize());
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            result.addAll(embedBatch(texts.subList(i, end)));
        }
        return result;
    }

    /**
     * 单批向量化（一次 HTTP 请求）。
     *
     * @param batch 本批文本
     * @return 与入参顺序一致的向量列表
     */
    private List<double[]> embedBatch(List<String> batch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", batch);
        try {
            String requestJson = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("[Embedding] 接口返回异常, status={}, body={}",
                        response.statusCode(), truncate(response.body()));
                throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                        "向量化接口调用失败(HTTP " + response.statusCode() + ")");
            }
            return parseVectors(response.body(), batch.size());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Embedding] 调用失败, model={}", properties.getModel(), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                    "向量化服务调用失败，请检查 aidoc.llm.embedding 配置");
        }
    }

    /**
     * 解析响应并按 index 回填，保证与入参顺序一致。
     *
     * @param responseJson 响应体
     * @param expected     期望的向量条数
     * @return 向量列表
     */
    private List<double[]> parseVectors(String responseJson, int expected) {
        try {
            JsonNode data = objectMapper.readTree(responseJson).path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                        "向量化接口未返回有效数据");
            }
            List<double[]> slots = new ArrayList<>(expected);
            for (int i = 0; i < expected; i++) {
                slots.add(null);
            }
            int cursor = 0;
            for (JsonNode item : data) {
                double[] vector = toArray(item.path("embedding"));
                // index 缺省时按出现顺序回填
                int index = item.path("index").asInt(cursor++);
                if (index >= 0 && index < expected) {
                    slots.set(index, vector);
                }
            }
            // 更新实际维度，供上层读取
            dimension = slots.isEmpty() || slots.get(0) == null ? dimension : slots.get(0).length;
            // 服务端漏返回的槽位补零向量，保持与入参一一对应
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i) == null) {
                    log.warn("[Embedding] 服务端未返回第 {} 条向量，已补零向量", i);
                    slots.set(i, new double[dimension]);
                }
            }
            return slots;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Embedding] 响应解析失败: {}", truncate(responseJson), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "向量化响应解析失败");
        }
    }

    /**
     * JSON 数组 → double 数组。
     *
     * @param node embedding 节点
     * @return 向量
     */
    private double[] toArray(JsonNode node) {
        double[] vector = new double[node.size()];
        for (int i = 0; i < node.size(); i++) {
            vector[i] = node.get(i).asDouble();
        }
        return vector;
    }

    /**
     * 拼接 embeddings 接口地址。
     *
     * @return 完整 URL
     */
    private String endpoint() {
        String base = properties.getBaseUrl();
        return base.endsWith("/") ? base + "embeddings" : base + "/embeddings";
    }

    /**
     * 截断超长文本（用于日志）。
     *
     * @param text 原文
     * @return 截断结果
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
