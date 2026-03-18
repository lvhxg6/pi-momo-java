package com.pi.agent.types;

/**
 * Callback invoked during tool execution to report intermediate progress updates.
 *
 * <p>Implementations receive partial {@link AgentToolResult} instances as the tool
 * produces incremental output. The Agent loop translates each invocation into a
 * {@code tool_execution_update} event so that subscribers can render real-time
 * progress in the UI.
 *
 * @see AgentToolResult
 */
@FunctionalInterface
public interface AgentToolUpdateCallback {

    /**
     * Called with a partial result produced during tool execution.
     *
     * @param partialResult the intermediate tool result
     */
    void onUpdate(AgentToolResult<?> partialResult);
}
