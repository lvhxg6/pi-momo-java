package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Session entry recording a change in the active model.
 *
 * <p>Records when the user switches to a different LLM provider or model.
 *
 * <p><b>Validates: Requirement 1.5</b>
 *
 * @param type      Always "model_change"
 * @param id        Unique entry identifier
 * @param parentId  Parent entry ID (null for first entry)
 * @param timestamp ISO 8601 timestamp
 * @param provider  The provider identifier (e.g., "anthropic", "openai")
 * @param modelId   The model identifier (e.g., "claude-3-opus")
 */
public record ModelChangeEntry(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("provider") String provider,
        @JsonProperty("modelId") String modelId
) implements SessionEntry {

    /**
     * Creates a new model change entry.
     *
     * @param id        Unique entry identifier
     * @param parentId  Parent entry ID
     * @param timestamp ISO 8601 timestamp
     * @param provider  The provider identifier
     * @param modelId   The model identifier
     * @return A new ModelChangeEntry
     */
    public static ModelChangeEntry create(String id, String parentId, String timestamp, String provider, String modelId) {
        return new ModelChangeEntry("model_change", id, parentId, timestamp, provider, modelId);
    }
}
