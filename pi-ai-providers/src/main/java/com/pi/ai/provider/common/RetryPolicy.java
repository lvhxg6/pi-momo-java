package com.pi.ai.provider.common;

import com.pi.ai.core.types.CancellationSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.regex.Pattern;

/**
 * HTTP 重试策略。
 *
 * <p>支持：
 * <ul>
 *   <li>429 状态码：使用 Retry-After 头延迟</li>
 *   <li>5xx 状态码：指数退避（1s, 2s, 4s）</li>
 *   <li>网络错误模式匹配（rate limit、overloaded 等）</li>
 *   <li>maxRetryDelayMs 超限时立即失败</li>
 *   <li>最大重试 3 次</li>
 * </ul>
 */
public final class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    /** 最大重试次数 */
    public static final int MAX_RETRIES = 3;

    /** 基础延迟（毫秒） */
    public static final long BASE_DELAY_MS = 1000;

    /** 默认最大重试延迟（毫秒），0 表示不限制 */
    public static final long DEFAULT_MAX_RETRY_DELAY_MS = 60000;

    /** 可重试的网络错误模式 */
    private static final Pattern RETRYABLE_ERROR_PATTERN = Pattern.compile(
            "rate.?limit|overloaded|service.?unavailable|upstream.?connect|connection.?refused",
            Pattern.CASE_INSENSITIVE
    );

    private RetryPolicy() {
        // 工具类，禁止实例化
    }

    /**
     * 判断 HTTP 状态码和错误文本是否可重试。
     *
     * @param statusCode HTTP 状态码
     * @param errorText  错误响应文本（可为 null）
     * @return 是否可重试
     */
    public static boolean isRetryable(int statusCode, String errorText) {
        // 429 Too Many Requests 或 5xx 服务端错误
        if (statusCode == 429 || (statusCode >= 500 && statusCode <= 504)) {
            return true;
        }
        // 匹配网络错误模式
        if (errorText != null && RETRYABLE_ERROR_PATTERN.matcher(errorText).find()) {
            return true;
        }
        return false;
    }

    /**
     * 计算重试延迟（毫秒）。
     *
     * <p>优先使用服务端提供的 Retry-After 头，否则使用指数退避。
     *
     * @param attempt       当前重试次数（0-based）
     * @param retryAfterMs  服务端 Retry-After 延迟（毫秒），-1 表示未提供
     * @return 延迟毫秒数
     */
    public static long calculateDelay(int attempt, long retryAfterMs) {
        if (retryAfterMs > 0) {
            return retryAfterMs;
        }
        // 指数退避：1s, 2s, 4s
        return BASE_DELAY_MS * (1L << attempt);
    }

    /**
     * 从 HTTP 响应中提取 Retry-After 延迟（毫秒）。
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>秒数（整数）：如 "30"</li>
     *   <li>HTTP 日期格式（暂不支持，返回 -1）</li>
     * </ul>
     *
     * @param response HTTP 响应
     * @return 延迟毫秒数，未提供或解析失败返回 -1
     */
    public static long extractRetryAfterMs(HttpResponse<?> response) {
        var retryAfter = response.headers().firstValue("retry-after").orElse(null);
        if (retryAfter == null || retryAfter.isBlank()) {
            return -1;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return seconds * 1000;
        } catch (NumberFormatException e) {
            log.debug("无法解析 Retry-After 头: {}", retryAfter);
            return -1;
        }
    }

    /**
     * 检查延迟是否超过最大重试延迟限制。
     *
     * @param delayMs         计算出的延迟毫秒数
     * @param maxRetryDelayMs 最大重试延迟限制（毫秒），null 或 0 表示使用默认值
     * @return 如果延迟超限返回 true
     */
    public static boolean exceedsMaxDelay(long delayMs, Integer maxRetryDelayMs) {
        long maxDelay = (maxRetryDelayMs != null && maxRetryDelayMs > 0)
                ? maxRetryDelayMs
                : DEFAULT_MAX_RETRY_DELAY_MS;
        return delayMs > maxDelay;
    }

    /**
     * 执行重试等待，支持取消信号中断。
     *
     * @param delayMs 等待毫秒数
     * @param signal  取消信号（可为 null）
     * @throws InterruptedException 如果等待被中断
     */
    public static void sleep(long delayMs, CancellationSignal signal) throws InterruptedException {
        if (signal != null && signal.isCancelled()) {
            throw new InterruptedException("请求已取消");
        }
        long remaining = delayMs;
        long start = System.currentTimeMillis();
        while (remaining > 0) {
            Thread.sleep(Math.min(remaining, 100)); // 每 100ms 检查一次取消信号
            if (signal != null && signal.isCancelled()) {
                throw new InterruptedException("请求已取消");
            }
            remaining = delayMs - (System.currentTimeMillis() - start);
        }
    }
}
