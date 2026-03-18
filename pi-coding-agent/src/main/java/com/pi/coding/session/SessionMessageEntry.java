package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.agent.types.AgentMessage;

/**
 * Session entry containing an agent message.
 *
 * <p>This is the most common entry type, storing user messages, assistant responses,
 * and tool results as part of the conversation history.
 *
 * <p><b>Validates: Requirement 1.3</b>
 *
 * @param type      Always "message"
 * @param id        Unique entry identifier
 * @param parentId  Parent entry ID (null for first entry)
 * @param timestamp ISO 8601 timestamp
 * @param message   The agent message content
 */
public record SessionMessageEntry(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("message") AgentMessage message
) implements SessionEntry {

    /**
     * Creates a new message entry.
     *
     * @param id        Unique entry identifier
     * @param parentId  Parent entry ID
     * @param timestamp ISO 8601 timestamp
     * @param message   The agent message
     * @return A new SessionMessageEntry
     */
    public static SessionMessageEntry create(String id, String parentId, String timestamp, AgentMessage message) {
        return new SessionMessageEntry("message", id, parentId, timestamp, message);
    }
}
