package com.pi.ai.core.types;

/**
 * Content block types that can appear in an {@code AssistantMessage}.
 *
 * <p>Permitted subtypes: {@link TextContent}, {@link ThinkingContent}, {@link ToolCall}.
 */
public sealed interface AssistantContentBlock extends ContentBlock permits TextContent, ThinkingContent, ToolCall {
}
