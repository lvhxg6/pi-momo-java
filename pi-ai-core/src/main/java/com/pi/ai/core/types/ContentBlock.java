package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base sealed interface for all content block types.
 *
 * <p>Uses Jackson polymorphic deserialization based on the {@code type} field
 * to dispatch to the correct concrete record type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ThinkingContent.class, name = "thinking"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = ToolCall.class, name = "toolCall")
})
public sealed interface ContentBlock permits UserContentBlock, AssistantContentBlock {

    /**
     * The discriminator value identifying the content block type.
     */
    String type();
}
