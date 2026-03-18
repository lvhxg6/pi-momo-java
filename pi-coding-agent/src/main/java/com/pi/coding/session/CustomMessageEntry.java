package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Custom message entry for extensions to inject messages into LLM context.
 *
 * <p>Unlike {@link CustomEntry}, this DOES participate in LLM context.
 * The content is converted to a user message in buildSessionContext().
 * Use details for extension-specific metadata (not sent to LLM).
 *
 * <p>The display flag controls TUI rendering:
 * <ul>
 *   <li>{@code false}: hidden entirely</li>
 *   <li>{@code true}: rendered with distinct styling (different from user messages)</li>
 * </ul>
 *
 * <p><b>Validates: Requirement 1.9</b>
 *
 * @param <T>        Type of extension-specific details
 * @param type       Always "custom_message"
 * @param id         Unique entry identifier
 * @param parentId   Parent entry ID (null for first entry)
 * @param timestamp  ISO 8601 timestamp
 * @param customType Extension identifier for filtering entries
 * @param content    Message content (String or List of ContentBlock)
 * @param display    Whether to show in TUI
 * @param details    Extension-specific metadata (nullable, not sent to LLM)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomMessageEntry<T>(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("customType") String customType,
        @JsonProperty("content") Object content,
        @JsonProperty("display") boolean display,
        @JsonProperty("details") T details
) implements SessionEntry {

    /**
     * Creates a new custom message entry.
     *
     * @param id         Unique entry identifier
     * @param parentId   Parent entry ID
     * @param timestamp  ISO 8601 timestamp
     * @param customType Extension identifier
     * @param content    Message content
     * @param display    Whether to show in TUI
     * @param details    Extension-specific details (nullable)
     * @param <T>        Type of details
     * @return A new CustomMessageEntry
     */
    public static <T> CustomMessageEntry<T> create(
            String id,
            String parentId,
            String timestamp,
            String customType,
            Object content,
            boolean display,
            T details
    ) {
        return new CustomMessageEntry<>("custom_message", id, parentId, timestamp, customType, content, display, details);
    }

    /**
     * Creates a new custom message entry without details.
     *
     * @param id         Unique entry identifier
     * @param parentId   Parent entry ID
     * @param timestamp  ISO 8601 timestamp
     * @param customType Extension identifier
     * @param content    Message content
     * @param display    Whether to show in TUI
     * @return A new CustomMessageEntry
     */
    public static CustomMessageEntry<Void> create(
            String id,
            String parentId,
            String timestamp,
            String customType,
            Object content,
            boolean display
    ) {
        return create(id, parentId, timestamp, customType, content, display, null);
    }
}
