package com.pi.coding.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.agent.types.AgentMessage;

/**
 * Message type for extension-injected messages via sendMessage().
 *
 * <p>These are custom messages that extensions can inject into the conversation.
 *
 * <p><b>Validates: Requirement 23.6</b>
 *
 * @param customType Extension identifier for filtering
 * @param content    Message content (String or List of ContentBlock)
 * @param display    Whether to show in TUI
 * @param details    Extension-specific metadata (nullable)
 * @param timestamp  Unix timestamp in milliseconds
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomMessage(
        @JsonProperty("customType") String customType,
        @JsonProperty("content") Object content,
        @JsonProperty("display") boolean display,
        @JsonProperty("details") Object details,
        @JsonProperty("timestamp") long timestamp
) implements AgentMessage {

    @Override
    public String role() {
        return "custom";
    }

    @Override
    public String toString() {
        return content instanceof String ? (String) content : String.valueOf(content);
    }
}
