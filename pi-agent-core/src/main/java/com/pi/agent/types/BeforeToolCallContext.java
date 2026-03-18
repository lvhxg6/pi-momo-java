package com.pi.agent.types;

import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.ToolCall;

/**
 * Context passed to the {@link com.pi.agent.config.BeforeToolCallHook} before a tool is executed.
 *
 * <p>Provides the hook with the assistant message that requested the tool call,
 * the raw tool call block, the validated arguments, and the current agent context.
 *
 * <p><b>Validates: Requirements 4.1</b>
 *
 * @param assistantMessage the assistant message that requested the tool call
 * @param toolCall         the raw tool call block from the assistant message content
 * @param args             validated tool arguments (may be a Map or other structure)
 * @param context          current agent context at the time the tool call is prepared
 */
public record BeforeToolCallContext(
        AssistantMessage assistantMessage,
        ToolCall toolCall,
        Object args,
        AgentContext context
) {
}
