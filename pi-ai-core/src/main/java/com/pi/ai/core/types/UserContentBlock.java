package com.pi.ai.core.types;

/**
 * Content block types that can appear in a {@code UserMessage} or {@code ToolResultMessage}.
 *
 * <p>Permitted subtypes: {@link TextContent}, {@link ImageContent}.
 */
public sealed interface UserContentBlock extends ContentBlock permits TextContent, ImageContent {
}
