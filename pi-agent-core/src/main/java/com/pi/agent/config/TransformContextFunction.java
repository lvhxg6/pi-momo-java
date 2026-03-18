package com.pi.agent.config;

import com.pi.agent.types.AgentMessage;
import com.pi.ai.core.types.CancellationSignal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Optional callback that transforms the {@link AgentMessage} list before
 * it is passed to {@link ConvertToLlmFunction}.
 *
 * <p>This hook enables context window management, message filtering,
 * summarization, or any other pre-processing of the conversation history.
 *
 * <p>Implementations must not throw exceptions; they should return the
 * original messages or a safe fallback value on failure.
 *
 * <p><b>Validates: Requirements 13.5</b>
 */
@FunctionalInterface
public interface TransformContextFunction {

    /**
     * Transforms the agent message list.
     *
     * @param messages the current agent message list
     * @param signal   cancellation signal for cooperative cancellation
     * @return a future resolving to the transformed message list
     */
    CompletableFuture<List<AgentMessage>> transform(List<AgentMessage> messages, CancellationSignal signal);
}
