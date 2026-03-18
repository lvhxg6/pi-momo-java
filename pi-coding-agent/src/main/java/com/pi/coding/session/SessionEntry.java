package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base sealed interface for all session entry types.
 *
 * <p>Session entries form a tree structure where each entry has an id and parentId.
 * The tree structure enables branching and navigation through conversation history.
 *
 * <p>Jackson polymorphic serialization is configured to dispatch based on the "type" field.
 *
 * <p><b>Validates: Requirements 1.3-1.11</b>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionMessageEntry.class, name = "message"),
    @JsonSubTypes.Type(value = ThinkingLevelChangeEntry.class, name = "thinking_level_change"),
    @JsonSubTypes.Type(value = ModelChangeEntry.class, name = "model_change"),
    @JsonSubTypes.Type(value = CompactionEntry.class, name = "compaction"),
    @JsonSubTypes.Type(value = BranchSummaryEntry.class, name = "branch_summary"),
    @JsonSubTypes.Type(value = CustomEntry.class, name = "custom"),
    @JsonSubTypes.Type(value = CustomMessageEntry.class, name = "custom_message"),
    @JsonSubTypes.Type(value = LabelEntry.class, name = "label"),
    @JsonSubTypes.Type(value = SessionInfoEntry.class, name = "session_info")
})
public sealed interface SessionEntry permits
        SessionMessageEntry,
        ThinkingLevelChangeEntry,
        ModelChangeEntry,
        CompactionEntry,
        BranchSummaryEntry,
        CustomEntry,
        CustomMessageEntry,
        LabelEntry,
        SessionInfoEntry {

    /**
     * The type discriminator for this entry (e.g., "message", "compaction").
     */
    String type();

    /**
     * Unique identifier for this entry within the session.
     */
    String id();

    /**
     * Parent entry ID, or null if this is the first entry after the header.
     */
    String parentId();

    /**
     * ISO 8601 timestamp when this entry was created.
     */
    String timestamp();
}
