package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Session metadata entry for user-defined display name.
 *
 * <p>Allows users to give sessions meaningful names for easier identification
 * in session lists.
 *
 * <p><b>Validates: Requirement 1.11</b>
 *
 * @param type      Always "session_info"
 * @param id        Unique entry identifier
 * @param parentId  Parent entry ID (null for first entry)
 * @param timestamp ISO 8601 timestamp
 * @param name      User-defined session display name (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionInfoEntry(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("name") String name
) implements SessionEntry {

    /**
     * Creates a new session info entry.
     *
     * @param id        Unique entry identifier
     * @param parentId  Parent entry ID
     * @param timestamp ISO 8601 timestamp
     * @param name      Session display name
     * @return A new SessionInfoEntry
     */
    public static SessionInfoEntry create(
            String id,
            String parentId,
            String timestamp,
            String name
    ) {
        return new SessionInfoEntry("session_info", id, parentId, timestamp, name);
    }
}
