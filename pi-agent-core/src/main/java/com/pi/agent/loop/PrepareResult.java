package com.pi.agent.loop;

import com.pi.agent.types.AgentTool;
import com.pi.agent.types.AgentToolResult;
import com.pi.ai.core.types.ToolCall;

/**
 * Result of preparing a tool call for execution.
 *
 * <ul>
 *   <li>{@link Prepared} — all checks passed; the tool is ready to execute.</li>
 *   <li>{@link Immediate} — an error was detected (tool not found, validation failure,
 *       or blocked by hook); contains the error result to return directly.</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 20.1, 20.2, 20.3, 20.4, 20.5, 20.6, 20.7</b>
 */
sealed interface PrepareResult permits PrepareResult.Prepared, PrepareResult.Immediate {

    /**
     * Tool call passed all checks and is ready for execution.
     *
     * @param toolCall the original tool call from the assistant message
     * @param tool     the resolved AgentTool
     * @param args     the validated arguments
     */
    record Prepared(ToolCall toolCall, AgentTool tool, Object args) implements PrepareResult {}

    /**
     * Tool call failed a check; contains the error result to return immediately.
     *
     * @param result  the error tool result
     * @param isError whether this is an error result
     */
    record Immediate(AgentToolResult<?> result, boolean isError) implements PrepareResult {}
}
