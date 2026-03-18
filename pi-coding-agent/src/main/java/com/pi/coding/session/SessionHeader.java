package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Session header record stored as the first line in a session JSONL file.
 *
 * <p>Contains metadata about the session including version, unique ID, timestamp,
 * working directory, and optional parent session reference for forked sessions.
 *
 * <p><b>Validates: Requirements 1.1, 1.2</b>
 *
 * @param type           Always "session" to identify this as a header entry
 * @param version        Session format version (current is {@link #CURRENT_VERSION})
 * @param id             Unique session identifier (UUID)
 * @param timestamp      ISO 8601 timestamp when session was created
 * @param cwd            Working directory where the session was started
 * @param parentSession  Path to parent session file if this session was forked (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionHeader(
        @JsonProperty("type") String type,
        @JsonProperty("version") int version,
        @JsonProperty("id") String id,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("cwd") String cwd,
        @JsonProperty("parentSession") String parentSession
) {
    /**
     * Current session format version.
     */
    public static final int CURRENT_VERSION = 3;

    /**
     * Creates a new session header with the current version.
     *
     * @param id            Unique session identifier
     * @param timestamp     ISO 8601 timestamp
     * @param cwd           Working directory
     * @param parentSession Optional parent session path (may be null)
     * @return A new SessionHeader instance
     */
    public static SessionHeader create(String id, String timestamp, String cwd, String parentSession) {
        return new SessionHeader("session", CURRENT_VERSION, id, timestamp, cwd, parentSession);
    }

    /**
     * Creates a new session header without a parent session.
     *
     * @param id        Unique session identifier
     * @param timestamp ISO 8601 timestamp
     * @param cwd       Working directory
     * @return A new SessionHeader instance
     */
    public static SessionHeader create(String id, String timestamp, String cwd) {
        return create(id, timestamp, cwd, null);
    }
}
