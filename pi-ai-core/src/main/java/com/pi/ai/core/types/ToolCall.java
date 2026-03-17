package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Tool call content block. Represents an LLM request to invoke a tool.
 * Appears only in assistant messages.
 *
 * @param type             always {@code "toolCall"}
 * @param id               unique identifier for this tool call
 * @param name             the tool name to invoke
 * @param arguments        the tool call arguments as a key-value map
 * @param thoughtSignature optional opaque signature for reusing thought context (Google-specific)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCall(
    @JsonProperty("type") String type,
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("arguments") Map<String, Object> arguments,
    @JsonProperty("thoughtSignature") String thoughtSignature
) implements AssistantContentBlock {

    /**
     * Convenience constructor without thoughtSignature.
     */
    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this("toolCall", id, name, arguments, null);
    }
}
