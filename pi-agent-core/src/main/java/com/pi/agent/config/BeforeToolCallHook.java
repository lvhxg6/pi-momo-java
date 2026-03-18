package com.pi.agent.config;

import com.pi.agent.types.BeforeToolCallContext;
import com.pi.agent.types.BeforeToolCallResult;
import com.pi.ai.core.types.CancellationSignal;

import java.util.concurrent.CompletableFuture;

/**
 * Hook invoked before a tool is executed.
 *
 * <p>When the returned {@link BeforeToolCallResult} has {@code block == true},
 * the agent loop prevents the tool from executing and generates an error
 * tool result containing the {@code reason} (or a default message).
 *
 * <p><b>Validates: Requirements 13.11</b>
 *
 * @see BeforeToolCallContext
 * @see BeforeToolCallResult
 */
@FunctionalInterface
public interface BeforeToolCallHook {

    /**
     * Evaluates whether the tool call should proceed.
     *
     * @param context the before-tool-call context with assistant message, tool call, args, and agent context
     * @param signal  cancellation signal for cooperative cancellation
     * @return a future resolving to the hook result (block/allow)
     */
    CompletableFuture<BeforeToolCallResult> call(BeforeToolCallContext context, CancellationSignal signal);
}
