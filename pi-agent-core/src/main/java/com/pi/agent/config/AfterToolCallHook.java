package com.pi.agent.config;

import com.pi.agent.types.AfterToolCallContext;
import com.pi.agent.types.AfterToolCallResult;
import com.pi.ai.core.types.CancellationSignal;

import java.util.concurrent.CompletableFuture;

/**
 * Hook invoked after a tool has executed.
 *
 * <p>The returned {@link AfterToolCallResult} allows field-level overrides
 * of the original tool execution result. Non-null fields replace the
 * originals; null fields preserve them.
 *
 * <p><b>Validates: Requirements 13.11</b>
 *
 * @see AfterToolCallContext
 * @see AfterToolCallResult
 */
@FunctionalInterface
public interface AfterToolCallHook {

    /**
     * Post-processes the tool execution result.
     *
     * @param context the after-tool-call context with assistant message, tool call, args, result, isError, and agent context
     * @param signal  cancellation signal for cooperative cancellation
     * @return a future resolving to the hook result (field-level overrides)
     */
    CompletableFuture<AfterToolCallResult> call(AfterToolCallContext context, CancellationSignal signal);
}
