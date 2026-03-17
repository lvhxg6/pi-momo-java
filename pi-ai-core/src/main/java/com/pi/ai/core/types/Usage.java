package com.pi.ai.core.types;

/**
 * Token usage statistics for a single LLM call.
 *
 * <p>Includes input/output/cache token counts and associated cost breakdown.
 * This record is immutable; use {@link MutableUsage} for streaming accumulation,
 * then convert via {@link MutableUsage#toUsage()}.
 */
public record Usage(
        int input,
        int output,
        int cacheRead,
        int cacheWrite,
        int totalTokens,
        Cost cost
) {

    /**
     * Cost breakdown for a single LLM call (in dollars).
     */
    public record Cost(
            double input,
            double output,
            double cacheRead,
            double cacheWrite,
            double total
    ) { }
}
