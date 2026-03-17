package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Unified message type for LLM conversations.
 *
 * <p>Uses Jackson polymorphic deserialization based on the {@code role} field
 * to dispatch to the correct concrete type.
 *
 * <p>Permitted subtypes:
 * <ul>
 *   <li>{@link UserMessage} — user input messages</li>
 *   <li>{@link AssistantMessage} — LLM response messages</li>
 *   <li>{@link ToolResultMessage} — tool execution result messages</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = ToolResultMessage.class, name = "toolResult")
})
public sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {

    /**
     * The role discriminator identifying the message type.
     */
    String role();

    /**
     * Unix timestamp in milliseconds when this message was created.
     */
    long timestamp();
}
