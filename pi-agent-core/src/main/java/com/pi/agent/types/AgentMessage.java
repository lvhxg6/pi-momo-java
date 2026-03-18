package com.pi.agent.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
 * <p>Jackson polymorphic serialization is configured to support {@link MessageAdapter}
 * as the default subtype. Custom implementations should register additional subtypes
 * via Jackson's {@code ObjectMapper} configuration if serialization is needed.
 *
 * <p><b>Validates: Requirements 8.1, 38.1, 38.4, 40.1</b>
 *
 * @see MessageAdapter
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@agentMessageType", defaultImpl = MessageAdapter.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = MessageAdapter.class, name = "messageAdapter")
})
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
