package com.pi.agent.types;

/**
 * Agent-layer message interface.
 *
 * <p>This is a <b>non-sealed</b> interface, allowing applications to define
 * custom message types (e.g., notifications, artifacts) alongside the standard
 * LLM message types ({@code UserMessage}, {@code AssistantMessage},
 * {@code ToolResultMessage}).
 *
 * <p>Standard LLM messages are adapted via {@link MessageAdapter}; custom
 * implementations only need to provide {@link #role()} and {@link #timestamp()}.
 *
 * <p><b>Validates: Requirements 8.1, 38.1, 38.4</b>
 *
 * @see MessageAdapter
 */
public interface AgentMessage {

    /**
     * The role discriminator identifying the message type
     * (e.g. {@code "user"}, {@code "assistant"}, {@code "toolResult"}, or a custom role).
     */
    String role();

    /**
     * Unix timestamp in milliseconds when this message was created.
     */
    long timestamp();
}
