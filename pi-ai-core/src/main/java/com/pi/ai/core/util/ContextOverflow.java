package com.pi.ai.core.util;

import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.StopReason;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 上下文溢出检测工具，检测 15+ 种 Provider 特定的溢出错误模式。
 *
 * <p>处理两种情况：
 * <ol>
 *   <li>错误型溢出：大多数 Provider 返回 stopReason=error 和特定错误消息</li>
 *   <li>静默溢出：部分 Provider（如 z.ai）接受溢出请求但 usage.input 超过 contextWindow</li>
 * </ol>
 *
 * <p>对应 TypeScript 中的 {@code utils/overflow.ts}。
 */
public final class ContextOverflow {

    /** Provider 特定的溢出错误模式 */
    private static final List<Pattern> OVERFLOW_PATTERNS = List.of(
            Pattern.compile("prompt is too long", Pattern.CASE_INSENSITIVE),
            Pattern.compile("input is too long for requested model", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the context window", Pattern.CASE_INSENSITIVE),
            Pattern.compile("input token count.*exceeds the maximum", Pattern.CASE_INSENSITIVE),
            Pattern.compile("maximum prompt length is \\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reduce the length of the messages", Pattern.CASE_INSENSITIVE),
            Pattern.compile("maximum context length is \\d+ tokens", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the limit of \\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the available context size", Pattern.CASE_INSENSITIVE),
            Pattern.compile("greater than the context length", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context window exceeds limit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeded model token limit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("too large for model with \\d+ maximum context length", Pattern.CASE_INSENSITIVE),
            Pattern.compile("model_context_window_exceeded", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context[_ ]length[_ ]exceeded", Pattern.CASE_INSENSITIVE),
            Pattern.compile("too many tokens", Pattern.CASE_INSENSITIVE),
            Pattern.compile("token limit exceeded", Pattern.CASE_INSENSITIVE)
    );

    /** Cerebras 特殊模式：400/413 状态码无响应体 */
    private static final Pattern CEREBRAS_PATTERN =
            Pattern.compile("^4(00|13)\\s*(status code)?\\s*\\(no body\\)", Pattern.CASE_INSENSITIVE);

    private ContextOverflow() {
        // 工具类，禁止实例化
    }

    /**
     * 检测助手消息是否表示上下文溢出错误。
     *
     * @param message       助手消息
     * @param contextWindow 上下文窗口大小（用于检测静默溢出，传 0 或负数跳过）
     * @return 是否为上下文溢出
     */
    public static boolean isContextOverflow(AssistantMessage message, int contextWindow) {
        if (message == null) {
            return false;
        }

        // Case 1: 检查错误消息模式
        if (message.getStopReason() == StopReason.ERROR && message.getErrorMessage() != null) {
            String errorMsg = message.getErrorMessage();

            // 检查已知模式
            for (Pattern pattern : OVERFLOW_PATTERNS) {
                if (pattern.matcher(errorMsg).find()) {
                    return true;
                }
            }

            // Cerebras 特殊处理
            if (CEREBRAS_PATTERN.matcher(errorMsg).find()) {
                return true;
            }
        }

        // Case 2: 静默溢出（z.ai 风格）— 成功但 usage 超过 contextWindow
        if (contextWindow > 0 && message.getStopReason() == StopReason.STOP && message.getUsage() != null) {
            int inputTokens = message.getUsage().input() + message.getUsage().cacheRead();
            if (inputTokens > contextWindow) {
                return true;
            }
        }

        return false;
    }

    /**
     * 返回溢出模式列表（用于测试）。
     */
    public static List<Pattern> getOverflowPatterns() {
        return OVERFLOW_PATTERNS;
    }
}
