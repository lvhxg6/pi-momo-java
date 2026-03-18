package com.pi.agent.config;

import com.pi.agent.types.AgentMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Optional callback that returns pending steering messages to inject
 * into the agent loop.
 *
 * <p>The agent loop polls this function after each tool execution round.
 * When steering messages are present, they are appended to the context
 * and a new LLM call is triggered before checking for follow-up messages.
 *
 * <p><b>Validates: Requirements 13.8</b>
 */
@FunctionalInterface
public interface GetSteeringMessagesFunction {

    /**
     * Returns pending steering messages.
     *
     * @return a future resolving to a list of steering messages, or an empty list
     */
    CompletableFuture<List<AgentMessage>> get();
}
