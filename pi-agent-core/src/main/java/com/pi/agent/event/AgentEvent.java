package com.pi.agent.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.AgentToolResult;
import com.pi.ai.core.event.AssistantMessageEvent;
import com.pi.ai.core.types.ToolResultMessage;

import java.util.List;

/**
 * Agent lifecycle events emitted during an agent loop run.
 *
 * <p>Events are grouped into four categories:
 * <ul>
 *   <li>Agent lifecycle: {@link AgentStart}, {@link AgentEnd}</li>
 *   <li>Turn lifecycle: {@link TurnStart}, {@link TurnEnd}</li>
 *   <li>Message lifecycle: {@link MessageStart}, {@link MessageUpdate}, {@link MessageEnd}</li>
 *   <li>Tool execution lifecycle: {@link ToolExecutionStart}, {@link ToolExecutionUpdate}, {@link ToolExecutionEnd}</li>
 * </ul>
 *
 * <p>Uses Jackson polymorphic serialization based on the {@code type} discriminator field.
 *
 * <p><b>Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.10</b>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = AgentEvent.AgentStart.class, name = "agent_start"),
    @JsonSubTypes.Type(value = AgentEvent.AgentEnd.class, name = "agent_end"),
    @JsonSubTypes.Type(value = AgentEvent.TurnStart.class, name = "turn_start"),
    @JsonSubTypes.Type(value = AgentEvent.TurnEnd.class, name = "turn_end"),
    @JsonSubTypes.Type(value = AgentEvent.MessageStart.class, name = "message_start"),
    @JsonSubTypes.Type(value = AgentEvent.MessageUpdate.class, name = "message_update"),
    @JsonSubTypes.Type(value = AgentEvent.MessageEnd.class, name = "message_end"),
    @JsonSubTypes.Type(value = AgentEvent.ToolExecutionStart.class, name = "tool_execution_start"),
    @JsonSubTypes.Type(value = AgentEvent.ToolExecutionUpdate.class, name = "tool_execution_update"),
    @JsonSubTypes.Type(value = AgentEvent.ToolExecutionEnd.class, name = "tool_execution_end")
})
public sealed interface AgentEvent
        permits AgentEvent.AgentStart,
                AgentEvent.AgentEnd,
                AgentEvent.TurnStart,
                AgentEvent.TurnEnd,
                AgentEvent.MessageStart,
                AgentEvent.MessageUpdate,
                AgentEvent.MessageEnd,
                AgentEvent.ToolExecutionStart,
                AgentEvent.ToolExecutionUpdate,
                AgentEvent.ToolExecutionEnd {

    /**
     * Event type discriminator string.
     */
    String type();

    // ── Agent lifecycle ──────────────────────────────────────────────────

    /** Emitted when the agent loop starts. */
    record AgentStart() implements AgentEvent {
        @Override
        public String type() { return "agent_start"; }
    }

    /**
     * Emitted when the agent loop ends.
     *
     * @param messages all new messages produced during this run
     */
    record AgentEnd(List<AgentMessage> messages) implements AgentEvent {
        @Override
        public String type() { return "agent_end"; }
    }

    // ── Turn lifecycle ───────────────────────────────────────────────────

    /** Emitted at the start of each turn (one LLM call + optional tool executions). */
    record TurnStart() implements AgentEvent {
        @Override
        public String type() { return "turn_start"; }
    }

    /**
     * Emitted at the end of each turn.
     *
     * @param message     the assistant message for this turn
     * @param toolResults tool result messages produced during this turn (empty if none)
     */
    record TurnEnd(AgentMessage message, List<ToolResultMessage> toolResults) implements AgentEvent {
        @Override
        public String type() { return "turn_end"; }
    }

    // ── Message lifecycle ────────────────────────────────────────────────

    /**
     * Emitted when a message enters the conversation (user, assistant, or toolResult).
     *
     * @param message the message being added
     */
    record MessageStart(AgentMessage message) implements AgentEvent {
        @Override
        public String type() { return "message_start"; }
    }

    /**
     * Emitted during assistant message streaming with each incremental update.
     *
     * @param message               the current partial assistant message
     * @param assistantMessageEvent  the underlying LLM streaming event
     */
    record MessageUpdate(AgentMessage message, AssistantMessageEvent assistantMessageEvent) implements AgentEvent {
        @Override
        public String type() { return "message_update"; }
    }

    /**
     * Emitted when a message is finalized in the conversation.
     *
     * @param message the finalized message
     */
    record MessageEnd(AgentMessage message) implements AgentEvent {
        @Override
        public String type() { return "message_end"; }
    }

    // ── Tool execution lifecycle ─────────────────────────────────────────

    /**
     * Emitted when a tool execution begins.
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName   name of the tool being executed
     * @param args       validated tool arguments
     */
    record ToolExecutionStart(String toolCallId, String toolName, Object args) implements AgentEvent {
        @Override
        public String type() { return "tool_execution_start"; }
    }

    /**
     * Emitted during tool execution with partial results (streaming updates).
     *
     * @param toolCallId    unique identifier for this tool call
     * @param toolName      name of the tool being executed
     * @param args          validated tool arguments
     * @param partialResult partial result from the tool's onUpdate callback
     */
    record ToolExecutionUpdate(String toolCallId, String toolName, Object args, AgentToolResult<?> partialResult) implements AgentEvent {
        @Override
        public String type() { return "tool_execution_update"; }
    }

    /**
     * Emitted when a tool execution completes.
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName   name of the tool that was executed
     * @param result     final tool execution result
     * @param isError    whether the tool execution resulted in an error
     */
    record ToolExecutionEnd(String toolCallId, String toolName, AgentToolResult<?> result, boolean isError) implements AgentEvent {
        @Override
        public String type() { return "tool_execution_end"; }
    }
}
