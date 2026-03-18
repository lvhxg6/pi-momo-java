package com.pi.agent.types;

import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.ToolCall;

/**
 * Context passed to the {@link com.pi.agent.config.AfterToolCallHook} after a tool has executed.
 *
 * <p>Provides the hook with the assistant message, tool call block, validated arguments,
 * the execution result, error flag, and the current agent context so the hook can
 * inspect or override the tool result.
 *
 * <p><b>Validates: Requirements 4.2</b>
 *
 * @param assistantMessage the assistant message that requested the tool call
 * @param toolCall         the raw tool call block from the assistant message content
 * @param args             validated tool arguments (may be a Map or other structure)
 * @param result           the executed tool result before any afterToolCall overrides
 * @param isError          whether the executed tool result is currently treated as an error
 * @param context          current agent context at the time the tool call is finalized
 */
public record AfterToolCallContext(
        AssistantMessage assistantMessage,
        ToolCall toolCall,
        Object args,
        AgentToolResult<?> result,
        boolean isError,
        AgentContext context
) {
}
