package com.pi.agent.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.pi.ai.core.types.Message;

/**
 * Adapter that wraps a pi-ai-core {@link Message} as an {@link AgentMessage}.
 *
 * <p>Because {@code Message} is a {@code sealed interface} (permitting only
 * {@code UserMessage}, {@code AssistantMessage}, and {@code ToolResultMessage}),
 * we cannot add {@code AgentMessage} to its permit list. This record bridges
 * the two type hierarchies by delegation.
 *
 * <h3>Static helpers</h3>
 * <ul>
 *   <li>{@link #wrap(Message)} — create a {@code MessageAdapter} from a {@code Message}</li>
 *   <li>{@link #unwrap(AgentMessage)} — extract the underlying {@code Message}
 *       (throws if the argument is not a {@code MessageAdapter})</li>
 *   <li>{@link #isLlmMessage(AgentMessage)} — check whether the message wraps
 *       a standard LLM {@code Message}</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 8.2, 8.3, 8.4, 8.5, 38.2, 40.1</b>
 *
 * @param message the wrapped pi-ai-core {@link Message}
 */
@JsonTypeName("messageAdapter")
public record MessageAdapter(@JsonProperty("message") Message message) implements AgentMessage {

    /**
     * Creates a {@code MessageAdapter} with a non-null message.
     *
     * @throws NullPointerException if {@code message} is null
     */
    public MessageAdapter {
        if (message == null) {
            throw new NullPointerException("message must not be null");
        }
    }

    @Override
    public String role() {
        return message.role();
    }

    @Override
    public long timestamp() {
        return message.timestamp();
    }

    // ---- Static factory / utility methods ----

    /**
     * Wraps a pi-ai-core {@link Message} as an {@link AgentMessage}.
     *
     * @param message the message to wrap
     * @return a new {@code MessageAdapter}
     * @throws NullPointerException if {@code message} is null
     */
    public static AgentMessage wrap(Message message) {
        return new MessageAdapter(message);
    }

    /**
     * Extracts the underlying pi-ai-core {@link Message} from an {@link AgentMessage}.
     *
     * @param agentMessage the agent message to unwrap
     * @return the underlying {@code Message}
     * @throws IllegalArgumentException if {@code agentMessage} is not a {@code MessageAdapter}
     */
    public static Message unwrap(AgentMessage agentMessage) {
        if (agentMessage instanceof MessageAdapter adapter) {
            return adapter.message();
        }
        throw new IllegalArgumentException(
                "Not a wrapped Message: " + agentMessage.getClass().getName());
    }

    /**
     * Returns {@code true} if the given {@link AgentMessage} wraps a standard
     * LLM {@link Message} (i.e., is a {@code MessageAdapter}).
     *
     * @param agentMessage the message to check
     * @return {@code true} if the message is a wrapped LLM message
     */
    public static boolean isLlmMessage(AgentMessage agentMessage) {
        return agentMessage instanceof MessageAdapter;
    }
}
