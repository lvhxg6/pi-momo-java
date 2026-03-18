package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Custom entry for extensions to store extension-specific data in the session.
 *
 * <p>Use customType to identify your extension's entries. On session reload,
 * extensions can scan entries for their customType and reconstruct internal state.
 *
 * <p><b>Important:</b> This entry does NOT participate in LLM context (ignored by
 * buildSessionContext). For injecting content into context, use {@link CustomMessageEntry}.
 *
 * <p><b>Validates: Requirement 1.8</b>
 *
 * @param <T>        Type of extension-specific data
 * @param type       Always "custom"
 * @param id         Unique entry identifier
 * @param parentId   Parent entry ID (null for first entry)
 * @param timestamp  ISO 8601 timestamp
 * @param customType Extension identifier for filtering entries
 * @param data       Extension-specific data (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomEntry<T>(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("customType") String customType,
        @JsonProperty("data") T data
) implements SessionEntry {

    /**
     * Creates a new custom entry.
     *
     * @param id         Unique entry identifier
     * @param parentId   Parent entry ID
     * @param timestamp  ISO 8601 timestamp
     * @param customType Extension identifier
     * @param data       Extension-specific data (nullable)
     * @param <T>        Type of data
     * @return A new CustomEntry
     */
    public static <T> CustomEntry<T> create(
            String id,
            String parentId,
            String timestamp,
            String customType,
            T data
    ) {
        return new CustomEntry<>("custom", id, parentId, timestamp, customType, data);
    }

    /**
     * Creates a new custom entry without data.
     *
     * @param id         Unique entry identifier
     * @param parentId   Parent entry ID
     * @param timestamp  ISO 8601 timestamp
     * @param customType Extension identifier
     * @return A new CustomEntry
     */
    public static CustomEntry<Void> create(
            String id,
            String parentId,
            String timestamp,
            String customType
    ) {
        return create(id, parentId, timestamp, customType, null);
    }
}
