package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Session entry recording a change in thinking level.
 *
 * <p>Thinking levels control the amount of reasoning the model performs
 * (e.g., "off", "low", "medium", "high").
 *
 * <p><b>Validates: Requirement 1.4</b>
 *
 * @param type          Always "thinking_level_change"
 * @param id            Unique entry identifier
 * @param parentId      Parent entry ID (null for first entry)
 * @param timestamp     ISO 8601 timestamp
 * @param thinkingLevel The new thinking level value
 */
public record ThinkingLevelChangeEntry(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("thinkingLevel") String thinkingLevel
) implements SessionEntry {

    /**
     * Creates a new thinking level change entry.
     *
     * @param id            Unique entry identifier
     * @param parentId      Parent entry ID
     * @param timestamp     ISO 8601 timestamp
     * @param thinkingLevel The new thinking level
     * @return A new ThinkingLevelChangeEntry
     */
    public static ThinkingLevelChangeEntry create(String id, String parentId, String timestamp, String thinkingLevel) {
        return new ThinkingLevelChangeEntry("thinking_level_change", id, parentId, timestamp, thinkingLevel);
    }
}
