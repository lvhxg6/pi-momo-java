package com.pi.agent.config;

import com.pi.agent.types.AgentMessage;
import com.pi.ai.core.types.Message;

import java.util.List;

/**
 * Callback that converts a list of {@link AgentMessage}s to pi-ai-java
 * {@link Message}s suitable for LLM consumption.
 *
 * <p>The default implementation filters out non-LLM messages (custom types)
 * and unwraps {@link com.pi.agent.types.MessageAdapter} instances.
 *
 * <p>Implementations must not throw exceptions; they should return a safe
 * fallback value (e.g. an empty list) on failure.
 *
 * <p><b>Validates: Requirements 13.3</b>
 */
@FunctionalInterface
public interface ConvertToLlmFunction {

    /**
     * Converts agent-layer messages to LLM-compatible messages.
     *
     * @param messages the agent message list to convert
     * @return a list of pi-ai-java {@link Message} instances
     */
    List<Message> convert(List<AgentMessage> messages);
}
