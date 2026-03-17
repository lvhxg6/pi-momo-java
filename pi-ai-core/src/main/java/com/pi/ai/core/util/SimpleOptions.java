package com.pi.ai.core.util;

import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.SimpleStreamOptions;
import com.pi.ai.core.types.StreamOptions;
import com.pi.ai.core.types.ThinkingBudgets;
import com.pi.ai.core.types.ThinkingLevel;

/**
 * 选项构建工具，用于从 {@link SimpleStreamOptions} 构建 {@link StreamOptions}。
 *
 * <p>对应 TypeScript 中的 {@code simple-options.ts}。
 */
public final class SimpleOptions {

    /** 默认思考 token 预算 */
    private static final int DEFAULT_MINIMAL = 1024;
    private static final int DEFAULT_LOW = 2048;
    private static final int DEFAULT_MEDIUM = 8192;
    private static final int DEFAULT_HIGH = 16384;

    /** 最小输出 token 数 */
    private static final int MIN_OUTPUT_TOKENS = 1024;

    private SimpleOptions() {
        // 工具类，禁止实例化
    }

    /**
     * 从 SimpleStreamOptions 构建基础 StreamOptions。
     *
     * <p>maxTokens 默认值为 {@code Math.min(model.maxTokens(), 32000)}。
     *
     * @param model   模型定义
     * @param options 简化选项（可为 null）
     * @param apiKey  API Key（可为 null，优先于 options 中的 apiKey）
     * @return 构建好的 StreamOptions
     */
    public static StreamOptions buildBaseOptions(Model model, SimpleStreamOptions options, String apiKey) {
        int maxTokens = (options != null && options.getMaxTokens() != null)
                ? options.getMaxTokens()
                : Math.min(model.maxTokens(), 32000);

        var builder = StreamOptions.builder()
                .maxTokens(maxTokens);

        if (options != null) {
            builder.temperature(options.getTemperature())
                    .signal(options.getSignal())
                    .cacheRetention(options.getCacheRetention())
                    .sessionId(options.getSessionId())
                    .headers(options.getHeaders())
                    .onPayload(options.getOnPayload())
                    .maxRetryDelayMs(options.getMaxRetryDelayMs())
                    .metadata(options.getMetadata());
        }

        // apiKey 参数优先于 options 中的 apiKey
        String resolvedApiKey = apiKey != null ? apiKey : (options != null ? options.getApiKey() : null);
        builder.apiKey(resolvedApiKey);

        return builder.build();
    }

    /**
     * 将 xhigh 思考级别降级为 high，其他级别不变。
     *
     * @param effort 思考级别（可为 null）
     * @return 降级后的思考级别，null 输入返回 null
     */
    public static ThinkingLevel clampReasoning(ThinkingLevel effort) {
        if (effort == ThinkingLevel.XHIGH) {
            return ThinkingLevel.HIGH;
        }
        return effort;
    }

    /**
     * 根据思考级别调整 maxTokens 和 thinkingBudget。
     *
     * @param baseMaxTokens  基础 maxTokens
     * @param modelMaxTokens 模型最大 token 数
     * @param reasoningLevel 思考级别
     * @param customBudgets  自定义预算（可为 null，使用默认值）
     * @return 调整后的 maxTokens 和 thinkingBudget
     */
    public static ThinkingResult adjustMaxTokensForThinking(
            int baseMaxTokens,
            int modelMaxTokens,
            ThinkingLevel reasoningLevel,
            ThinkingBudgets customBudgets
    ) {
        // 合并默认预算和自定义预算
        int minimal = customBudgets != null && customBudgets.minimal() != null ? customBudgets.minimal() : DEFAULT_MINIMAL;
        int low = customBudgets != null && customBudgets.low() != null ? customBudgets.low() : DEFAULT_LOW;
        int medium = customBudgets != null && customBudgets.medium() != null ? customBudgets.medium() : DEFAULT_MEDIUM;
        int high = customBudgets != null && customBudgets.high() != null ? customBudgets.high() : DEFAULT_HIGH;

        ThinkingLevel level = clampReasoning(reasoningLevel);
        int thinkingBudget = switch (level) {
            case MINIMAL -> minimal;
            case LOW -> low;
            case MEDIUM -> medium;
            case HIGH -> high;
            case XHIGH -> high; // 不应到达，clampReasoning 已处理
        };

        int maxTokens = Math.min(baseMaxTokens + thinkingBudget, modelMaxTokens);

        if (maxTokens <= thinkingBudget) {
            thinkingBudget = Math.max(0, maxTokens - MIN_OUTPUT_TOKENS);
        }

        return new ThinkingResult(maxTokens, thinkingBudget);
    }

    /**
     * adjustMaxTokensForThinking 的返回值。
     *
     * @param maxTokens      调整后的 maxTokens
     * @param thinkingBudget 思考 token 预算
     */
    public record ThinkingResult(int maxTokens, int thinkingBudget) { }
}
