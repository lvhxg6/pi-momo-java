package com.pi.agent.config;

import com.pi.agent.types.AgentMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Optional callback that returns pending follow-up messages.
 *
 * <p>The agent loop polls this function when there are no more tool calls
 * and no steering messages. When follow-up messages are present, they
 * trigger a new outer loop iteration.
 *
 * <p><b>Validates: Requirements 13.9</b>
 */
@FunctionalInterface
public interface GetFollowUpMessagesFunction {

    /**
     * Returns pending follow-up messages.
     *
     * @return a future resolving to a list of follow-up messages, or an empty list
     */
    CompletableFuture<List<AgentMessage>> get();
}
