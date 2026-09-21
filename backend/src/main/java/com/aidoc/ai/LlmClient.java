package com.aidoc.ai;

import com.aidoc.common.BusinessException;
import com.aidoc.common.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 大模型 HTTP 客户端（OpenAI 兼容 chat/completions 协议，流式 SSE）。
 *
 * <p>通过 JDK17 {@link HttpClient} 调用任意 OpenAI 兼容接口（DeepSeek / 通义 / 智谱 / OpenAI 等），
 * 以 {@code stream: true} 逐行解析 SSE 增量并回调 {@code onDelta}，实现打字机式输出。</p>
 *
 * <p>配置见 application.yml 的 aidoc.llm.* 段：base-url / api-key / model /
 * temperature / max-tokens / 超时 / 上下文最大字符数。</p>
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aidoc.llm")
public class LlmClient {

    /** 接口基址（不含 /chat/completions 后缀） */
    private String baseUrl;

    /** API Key（为空时拒绝调用并给出友好提示） */
    private String apiKey;

    /** 模型名 */
    private String model;

    /** 采样温度 */
    private double temperature = 0.3;

    /** 单次最大回复 token */
    private int maxTokens = 2048;

    /** 建立连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 流式读取总超时（毫秒） */
    private int readTimeoutMs = 300000;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式调用大模型：逐段回调增量内容，全程同步阻塞（由 Servlet 请求线程执行并写 SSE）。
     *
     * @param messages 会话消息序列：[{role: system|user|assistant, content: "..."}]
     * @param onDelta  增量内容回调（每收到一段文本调用一次）
     */
    public void streamChat(List<Map<String, String>> messages, Consumer<String> onDelta) {
        // 1. API Key 未配置时给出明确提示（避免晦涩的 401）
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                    "尚未配置大模型 API Key，请修改 application.yml 的 aidoc.llm.api-key");
        }

        // 2. 组装请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);   // 关键：开启流式

        try {
            String json = objectMapper.writeValueAsString(body);
            // 3. 拼接完整 URL：{base}/chat/completions
            String endpoint = baseUrl.endsWith("/")
                    ? baseUrl + "chat/completions"
                    : baseUrl + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build();

            // 4. 发送请求并判断状态
            HttpResponse<Stream<String>> response = client.send(request,
                    HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() / 100 != 2) {
                String errBody;
                try (Stream<String> lines = response.body()) {
                    errBody = String.join("\n", lines.limit(20).toList());
                }
                throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                        "大模型接口调用失败(HTTP " + response.statusCode() + "): "
                                + truncate(errBody, 200));
            }

            // 5. 逐行解析 SSE：data: {json} / data: [DONE]
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> {
                    if (line == null || !line.startsWith("data:")) {
                        return;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        return;
                    }
                    try {
                        JsonNode root = objectMapper.readTree(payload);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).path("delta").path("content");
                            if (delta.isTextual() && !delta.asText().isEmpty()) {
                                // 增量文本回调给上层（写 SSE 事件 / 累积全文）
                                onDelta.accept(delta.asText());
                            }
                        }
                    } catch (Exception parseErr) {
                        // 个别事件行解析失败直接跳过，不中断整个流
                        log.debug("[LLM] 忽略无法解析的 SSE 行: {}", truncate(payload, 120));
                    }
                });
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LLM] 流式调用失败, model={}", model, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                    "大模型服务调用失败，请稍后重试");
        }
    }

    /**
     * 截断超长文本（用于错误信息，防止异常消息过长刷屏）。
     *
     * @param text 原文
     * @param max  最大长度
     * @return 截断结果
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
