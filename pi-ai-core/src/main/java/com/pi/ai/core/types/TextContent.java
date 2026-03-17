package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Text content block. Can appear in both user and assistant messages.
 *
 * @param type          always {@code "text"}
 * @param text          the text content
 * @param textSignature optional signature metadata (e.g. OpenAI responses message metadata)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextContent(
    @JsonProperty("type") String type,
    @JsonProperty("text") String text,
    @JsonProperty("textSignature") String textSignature
) implements UserContentBlock, AssistantContentBlock {

    /**
     * Convenience constructor without textSignature.
     */
    public TextContent(String text) {
        this("text", text, null);
    }
}
