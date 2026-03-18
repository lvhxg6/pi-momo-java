package com.pi.coding.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.agent.types.AgentMessage;

import java.util.List;

/**
 * Session context containing the resolved state for LLM interaction.
 *
 * <p>Built by walking the session tree from the current leaf to root,
 * collecting messages and extracting the current thinking level and model.
 * Handles compaction summaries and branch summaries along the path.
 *
 * <p><b>Validates: Requirement 1.12</b>
 *
 * @param messages      List of agent messages to send to the LLM
 * @param thinkingLevel Current thinking level (e.g., "off", "low", "medium", "high")
 * @param model         Current model information (nullable if not set)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionContext(
        @JsonProperty("messages") List<AgentMessage> messages,
        @JsonProperty("thinkingLevel") String thinkingLevel,
        @JsonProperty("model") ModelInfo model
) {
    /**
     * Model information containing provider and model ID.
     *
     * @param provider The provider identifier (e.g., "anthropic", "openai")
     * @param modelId  The model identifier (e.g., "claude-3-opus")
     */
    public record ModelInfo(
            @JsonProperty("provider") String provider,
            @JsonProperty("modelId") String modelId
    ) {
        /**
         * Creates a new ModelInfo.
         *
         * @param provider The provider identifier
         * @param modelId  The model identifier
         * @return A new ModelInfo instance
         */
        public static ModelInfo of(String provider, String modelId) {
            return new ModelInfo(provider, modelId);
        }
    }

    /**
     * Creates an empty session context with default values.
     *
     * @return A new SessionContext with empty messages, "off" thinking level, and null model
     */
    public static SessionContext empty() {
        return new SessionContext(List.of(), "off", null);
    }

    /**
     * Creates a session context with the given messages and default settings.
     *
     * @param messages The list of agent messages
     * @return A new SessionContext with "off" thinking level and null model
     */
    public static SessionContext of(List<AgentMessage> messages) {
        return new SessionContext(messages, "off", null);
    }

    /**
     * Creates a session context with all fields specified.
     *
     * @param messages      The list of agent messages
     * @param thinkingLevel The thinking level
     * @param provider      The model provider (nullable)
     * @param modelId       The model ID (nullable)
     * @return A new SessionContext
     */
    public static SessionContext of(List<AgentMessage> messages, String thinkingLevel, String provider, String modelId) {
        ModelInfo model = (provider != null && modelId != null) ? ModelInfo.of(provider, modelId) : null;
        return new SessionContext(messages, thinkingLevel, model);
    }
}
