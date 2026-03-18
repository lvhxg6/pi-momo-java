package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Label entry for user-defined bookmarks/markers on entries.
 *
 * <p>Labels allow users to mark specific points in the conversation for
 * easy navigation. Pass null or empty string to clear a label.
 *
 * <p><b>Validates: Requirement 1.10</b>
 *
 * @param type      Always "label"
 * @param id        Unique entry identifier
 * @param parentId  Parent entry ID (null for first entry)
 * @param timestamp ISO 8601 timestamp
 * @param targetId  ID of the entry being labeled
 * @param label     The label text (null to clear)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LabelEntry(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("targetId") String targetId,
        @JsonProperty("label") String label
) implements SessionEntry {

    /**
     * Creates a new label entry.
     *
     * @param id        Unique entry identifier
     * @param parentId  Parent entry ID
     * @param timestamp ISO 8601 timestamp
     * @param targetId  ID of the entry to label
     * @param label     The label text (null to clear)
     * @return A new LabelEntry
     */
    public static LabelEntry create(
            String id,
            String parentId,
            String timestamp,
            String targetId,
            String label
    ) {
        return new LabelEntry("label", id, parentId, timestamp, targetId, label);
    }
}
