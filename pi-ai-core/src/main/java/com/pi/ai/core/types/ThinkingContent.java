package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Thinking content block. Appears only in assistant messages when the model
 * uses extended thinking / reasoning.
 *
 * @param type              always {@code "thinking"}
 * @param thinking          the thinking text
 * @param thinkingSignature optional opaque signature for multi-turn reasoning continuity
 * @param redacted          when {@code true}, the thinking content was redacted by safety filters;
 *                          the encrypted payload is stored in {@code thinkingSignature}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThinkingContent(
    @JsonProperty("type") String type,
    @JsonProperty("thinking") String thinking,
    @JsonProperty("thinkingSignature") String thinkingSignature,
    @JsonProperty("redacted") Boolean redacted
) implements AssistantContentBlock {

    /**
     * Convenience constructor with only thinking text.
     */
    public ThinkingContent(String thinking) {
        this("thinking", thinking, null, null);
    }
}
