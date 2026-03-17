package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User input message. Content can be either a plain text {@link String}
 * or a {@code List<UserContentBlock>} for mixed text/image content.
 *
 * <p>Jackson handles the polymorphic {@code content} field naturally:
 * a JSON string deserializes as {@code String}, a JSON array deserializes
 * as {@code List<UserContentBlock>}.
 *
 * @param role      always {@code "user"}
 * @param content   plain text string or list of {@link UserContentBlock}
 * @param timestamp Unix timestamp in milliseconds
 */
public record UserMessage(
    @JsonProperty("role") String role,
    @JsonProperty("content") Object content,
    @JsonProperty("timestamp") long timestamp
) implements Message {

    /**
     * Jackson deserialization constructor. The {@code role} property is consumed
     * by {@code @JsonTypeInfo} for type resolution, so it arrives as {@code null};
     * we default it to {@code "user"}.
     */
    @JsonCreator
    public UserMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") Object content,
        @JsonProperty("timestamp") long timestamp
    ) {
        this.role = role != null ? role : "user";
        this.content = content;
        this.timestamp = timestamp;
    }

    /**
     * Convenience constructor for plain text content.
     */
    public UserMessage(String textContent, long timestamp) {
        this("user", textContent, timestamp);
    }

    /**
     * Convenience constructor for structured content blocks.
     */
    public UserMessage(java.util.List<UserContentBlock> blocks, long timestamp) {
        this("user", blocks, timestamp);
    }
}
