package com.pi.ai.provider.common;

import com.pi.ai.core.event.AssistantMessageEvent;
import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.types.*;
import com.pi.ai.core.util.PiAiJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Provider 基类，提供 HTTP 请求、SSE 解析、重试和错误处理的通用基础设施。
 *
 * <p>每个具体 Provider 继承此类，实现消息转换、工具转换和 SSE 事件映射逻辑。
 * HTTP 请求在独立线程中执行（通过 CompletableFuture.runAsync），
 * 立即返回 AssistantMessageEventStream 给调用者。
 */
public abstract class BaseProvider {

    private static final Logger log = LoggerFactory.getLogger(BaseProvider.class);

    /** 共享 HttpClient 实例，支持 HTTP/2 */
    protected static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 构建 HTTP POST 请求。
     *
     * @param url     请求 URL
     * @param body    请求体 JSON 字符串
     * @param headers 自定义 HTTP 头
     * @return HttpRequest 实例
     */
    protected HttpRequest buildPostRequest(String url, String body, Map<String, String> headers) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream");

        if (headers != null) {
            headers.forEach(builder::header);
        }

        return builder.build();
    }

    /**
     * 发送 HTTP 请求并返回 InputStream 响应，支持重试。
     *
     * @param request HTTP 请求
     * @param options 流式调用选项（含 maxRetryDelayMs 和 signal）
     * @return HTTP 响应
     * @throws Exception 请求失败或重试耗尽
     */
    protected HttpResponse<InputStream> sendWithRetry(HttpRequest request, StreamOptions options) throws Exception {
        CancellationSignal signal = options != null ? options.getSignal() : null;
        Integer maxRetryDelayMs = options != null ? options.getMaxRetryDelayMs() : null;

        Exception lastError = null;

        for (int attempt = 0; attempt <= RetryPolicy.MAX_RETRIES; attempt++) {
            // 检查取消信号
            if (signal != null && signal.isCancelled()) {
                throw new InterruptedException("请求已取消");
            }

            try {
                HttpResponse<InputStream> response = HTTP_CLIENT.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());

                int status = response.statusCode();

                // 成功响应
                if (status >= 200 && status < 300) {
                    return response;
                }

                // 读取错误响应体
                String errorText = readErrorBody(response);

                // 判断是否可重试
                if (attempt < RetryPolicy.MAX_RETRIES && RetryPolicy.isRetryable(status, errorText)) {
                    long retryAfterMs = RetryPolicy.extractRetryAfterMs(response);
                    long delayMs = RetryPolicy.calculateDelay(attempt, retryAfterMs);

                    // 检查延迟是否超限
                    if (retryAfterMs > 0 && RetryPolicy.exceedsMaxDelay(retryAfterMs, maxRetryDelayMs)) {
                        long delaySec = Math.round(retryAfterMs / 1000.0);
                        throw new RuntimeException(
                                "服务端要求等待 " + delaySec + " 秒后重试，超过最大重试延迟限制");
                    }

                    log.debug("HTTP {} 错误，第 {} 次重试，延迟 {}ms", status, attempt + 1, delayMs);
                    RetryPolicy.sleep(delayMs, signal);
                    continue;
                }

                // 不可重试或重试耗尽
                throw new RuntimeException("HTTP " + status + " 错误: " + errorText);

            } catch (RuntimeException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
                // 网络错误可重试
                if (attempt < RetryPolicy.MAX_RETRIES) {
                    long delayMs = RetryPolicy.calculateDelay(attempt, -1);
                    log.debug("网络错误，第 {} 次重试，延迟 {}ms: {}", attempt + 1, delayMs, e.getMessage());
                    RetryPolicy.sleep(delayMs, signal);
                } else {
                    throw new RuntimeException("网络错误: " + e.getMessage(), e);
                }
            }
        }

        throw new RuntimeException("重试耗尽", lastError);
    }

    /**
     * 读取错误响应体文本。
     */
    private String readErrorBody(HttpResponse<InputStream> response) {
        try (InputStream is = response.body()) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(无法读取错误响应体)";
        }
    }

    /**
     * 创建初始的 AssistantMessage 输出对象（用于流式累积）。
     *
     * @param model 目标模型
     * @return 初始化的 AssistantMessage
     */
    protected AssistantMessage createInitialOutput(Model model) {
        return AssistantMessage.builder()
                .content(new ArrayList<>())
                .api(model.api())
                .provider(model.provider())
                .model(model.id())
                .usage(new Usage(0, 0, 0, 0, 0,
                        new Usage.Cost(0, 0, 0, 0, 0)))
                .stopReason(StopReason.STOP)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 发出错误事件并结束流。
     *
     * @param stream  事件流
     * @param output  当前累积的 AssistantMessage
     * @param error   异常
     * @param signal  取消信号（可为 null）
     */
    protected void emitError(AssistantMessageEventStream stream, AssistantMessage output,
                             Exception error, CancellationSignal signal) {
        StopReason reason = (signal != null && signal.isCancelled())
                ? StopReason.ABORTED
                : StopReason.ERROR;
        output.setStopReason(reason);
        output.setErrorMessage(error.getMessage());
        stream.push(new AssistantMessageEvent.Error(reason, output));
        stream.end(null);
    }

    /**
     * 合并多个 headers Map，后面的覆盖前面的。
     *
     * @param headerSources 多个 headers Map
     * @return 合并后的 headers
     */
    @SafeVarargs
    protected final Map<String, String> mergeHeaders(Map<String, String>... headerSources) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (Map<String, String> source : headerSources) {
            if (source != null) {
                merged.putAll(source);
            }
        }
        return merged;
    }

    /**
     * 将 Java 对象序列化为 JSON 字符串。
     */
    protected String toJson(Object obj) {
        try {
            return PiAiJson.MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /**
     * 将 JSON 字符串解析为 Map。
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseJson(String json) {
        try {
            return PiAiJson.MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败: " + json, e);
        }
    }
}
