package com.pi.agent.loop;

import com.pi.agent.types.AgentToolResult;

/**
 * Result of executing a prepared tool call.
 *
 * @param result  the tool execution result
 * @param isError whether the execution resulted in an error
 */
record ExecuteResult(AgentToolResult<?> result, boolean isError) {}
