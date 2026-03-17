package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Tool execution result message. Sent after a tool call completes.
 *
 * @param role       always {@code "toolResult"}
 * @param toolCallId the ID of the {@link ToolCall} this result corresponds to
 * @param toolName   the name of the tool that was invoked
 * @param content    result content blocks (text and/or images)
 * @param details    optional additional details (nullable, any JSON-serializable object)
 * @param isError    whether the tool execution resulted in an error
 * @param timestamp  Unix timestamp in milliseconds
 */
public record ToolResultMessage(
    @JsonProperty("role") String role,
    @JsonProperty("toolCallId") String toolCallId,
    @JsonProperty("toolName") String toolName,
    @JsonProperty("content") List<UserContentBlock> content,
    @JsonProperty("details") Object details,
    @JsonProperty("isError") boolean isError,
    @JsonProperty("timestamp") long timestamp
) implements Message {

    /**
     * Jackson deserialization constructor. The {@code role} property is consumed
     * by {@code @JsonTypeInfo} for type resolution, so it arrives as {@code null};
     * we default it to {@code "toolResult"}.
     */
    @JsonCreator
    public ToolResultMessage(
        @JsonProperty("role") String role,
        @JsonProperty("toolCallId") String toolCallId,
        @JsonProperty("toolName") String toolName,
        @JsonProperty("content") List<UserContentBlock> content,
        @JsonProperty("details") Object details,
        @JsonProperty("isError") boolean isError,
        @JsonProperty("timestamp") long timestamp
    ) {
        this.role = role != null ? role : "toolResult";
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.content = content;
        this.details = details;
        this.isError = isError;
        this.timestamp = timestamp;
    }

    /**
     * Convenience constructor that sets role automatically.
     */
    public ToolResultMessage(String toolCallId, String toolName,
                             List<UserContentBlock> content, Object details,
                             boolean isError, long timestamp) {
        this("toolResult", toolCallId, toolName, content, details, isError, timestamp);
    }
}
