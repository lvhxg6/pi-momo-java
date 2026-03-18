package com.pi.agent.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pi.ai.core.types.UserContentBlock;

import java.util.List;

/**
 * Result returned by an {@link com.pi.agent.types.AgentTool} execution.
 *
 * <p>{@code content} holds the text/image content blocks produced by the tool,
 * while {@code details} carries an optional tool-specific structured payload.
 *
 * @param content content blocks returned by the tool (text / image)
 * @param details tool-specific structured data
 * @param <T> type of the details payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentToolResult<T>(
        List<UserContentBlock> content,
        T details
) {
}
