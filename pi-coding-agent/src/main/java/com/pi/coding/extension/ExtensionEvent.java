package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base sealed interface for all extension event types.
 *
 * <p>Extension events are emitted during agent lifecycle and can be subscribed to
 * by extensions to react to and modify agent behavior.
 *
 * <p>Events are grouped into categories:
 * <ul>
 *   <li>Session events: session lifecycle (start, switch, fork, compact, tree, shutdown)</li>
 *   <li>Agent events: agent loop lifecycle (start, end, turn, message)</li>
 *   <li>Interception events: context, provider request, input, tool call/result</li>
 *   <li>Other events: model select, user bash, resources discover</li>
 * </ul>
 *
 * <p>Jackson polymorphic serialization is configured to dispatch based on the "type" field.
 *
 * <p><b>Validates: Requirements 6.1-6.24</b>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    // Resource events
    @JsonSubTypes.Type(value = ExtensionEvent.ResourcesDiscoverEvent.class, name = "resources_discover"),
    // Session events
    @JsonSubTypes.Type(value = ExtensionEvent.SessionDirectoryEvent.class, name = "session_directory"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionStartEvent.class, name = "session_start"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionBeforeSwitchEvent.class, name = "session_before_switch"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionSwitchEvent.class, name = "session_switch"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionBeforeForkEvent.class, name = "session_before_fork"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionForkEvent.class, name = "session_fork"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionBeforeCompactEvent.class, name = "session_before_compact"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionCompactEvent.class, name = "session_compact"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionBeforeTreeEvent.class, name = "session_before_tree"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionTreeEvent.class, name = "session_tree"),
    @JsonSubTypes.Type(value = ExtensionEvent.SessionShutdownEvent.class, name = "session_shutdown"),
    // Agent events
    @JsonSubTypes.Type(value = ExtensionEvent.BeforeAgentStartEvent.class, name = "before_agent_start"),
    @JsonSubTypes.Type(value = ExtensionEvent.AgentStartEvent.class, name = "agent_start"),
    @JsonSubTypes.Type(value = ExtensionEvent.AgentEndEvent.class, name = "agent_end"),
    @JsonSubTypes.Type(value = ExtensionEvent.TurnStartEvent.class, name = "turn_start"),
    @JsonSubTypes.Type(value = ExtensionEvent.TurnEndEvent.class, name = "turn_end"),
    @JsonSubTypes.Type(value = ExtensionEvent.MessageStartEvent.class, name = "message_start"),
    @JsonSubTypes.Type(value = ExtensionEvent.MessageUpdateEvent.class, name = "message_update"),
    @JsonSubTypes.Type(value = ExtensionEvent.MessageEndEvent.class, name = "message_end"),
    @JsonSubTypes.Type(value = ExtensionEvent.ToolExecutionStartEvent.class, name = "tool_execution_start"),
    @JsonSubTypes.Type(value = ExtensionEvent.ToolExecutionUpdateEvent.class, name = "tool_execution_update"),
    @JsonSubTypes.Type(value = ExtensionEvent.ToolExecutionEndEvent.class, name = "tool_execution_end"),
    // Interception events
    @JsonSubTypes.Type(value = ExtensionEvent.ContextEvent.class, name = "context"),
    @JsonSubTypes.Type(value = ExtensionEvent.BeforeProviderRequestEvent.class, name = "before_provider_request"),
    @JsonSubTypes.Type(value = ExtensionEvent.InputEvent.class, name = "input"),
    @JsonSubTypes.Type(value = ExtensionEvent.ToolCallEvent.class, name = "tool_call"),
    @JsonSubTypes.Type(value = ExtensionEvent.ToolResultEvent.class, name = "tool_result"),
    // Other events
    @JsonSubTypes.Type(value = ExtensionEvent.ModelSelectEvent.class, name = "model_select"),
    @JsonSubTypes.Type(value = ExtensionEvent.UserBashEvent.class, name = "user_bash")
})
public sealed interface ExtensionEvent permits
        // Resource events
        ExtensionEvent.ResourcesDiscoverEvent,
        // Session events
        ExtensionEvent.SessionDirectoryEvent,
        ExtensionEvent.SessionStartEvent,
        ExtensionEvent.SessionBeforeSwitchEvent,
        ExtensionEvent.SessionSwitchEvent,
        ExtensionEvent.SessionBeforeForkEvent,
        ExtensionEvent.SessionForkEvent,
        ExtensionEvent.SessionBeforeCompactEvent,
        ExtensionEvent.SessionCompactEvent,
        ExtensionEvent.SessionBeforeTreeEvent,
        ExtensionEvent.SessionTreeEvent,
        ExtensionEvent.SessionShutdownEvent,
        // Agent events
        ExtensionEvent.BeforeAgentStartEvent,
        ExtensionEvent.AgentStartEvent,
        ExtensionEvent.AgentEndEvent,
        ExtensionEvent.TurnStartEvent,
        ExtensionEvent.TurnEndEvent,
        ExtensionEvent.MessageStartEvent,
        ExtensionEvent.MessageUpdateEvent,
        ExtensionEvent.MessageEndEvent,
        ExtensionEvent.ToolExecutionStartEvent,
        ExtensionEvent.ToolExecutionUpdateEvent,
        ExtensionEvent.ToolExecutionEndEvent,
        // Interception events
        ExtensionEvent.ContextEvent,
        ExtensionEvent.BeforeProviderRequestEvent,
        ExtensionEvent.InputEvent,
        ExtensionEvent.ToolCallEvent,
        ExtensionEvent.ToolResultEvent,
        // Other events
        ExtensionEvent.ModelSelectEvent,
        ExtensionEvent.UserBashEvent {

    /**
     * Event type discriminator string.
     */
    String type();


    // ══════════════════════════════════════════════════════════════════════════
    // Resource Events
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fired after session_start to allow extensions to provide additional resource paths.
     *
     * <p><b>Validates: Requirement 6.1</b>
     *
     * @param cwd    current working directory
     * @param reason reason for discovery ("startup" or "reload")
     */
    record ResourcesDiscoverEvent(
        String cwd,
        String reason
    ) implements ExtensionEvent {
        @Override
        public String type() { return "resources_discover"; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Session Events
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fired before session manager creation to allow custom session directory resolution.
     *
     * <p><b>Validates: Requirement 6.2</b>
     *
     * @param cwd current working directory
     */
    record SessionDirectoryEvent(
        String cwd
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_directory"; }
    }

    /**
     * Fired on initial session load.
     *
     * <p><b>Validates: Requirement 6.3</b>
     */
    record SessionStartEvent() implements ExtensionEvent {
        @Override
        public String type() { return "session_start"; }
    }

    /**
     * Fired before switching to another session (can be cancelled).
     *
     * <p><b>Validates: Requirement 6.4</b>
     *
     * @param reason            reason for switch ("new" or "resume")
     * @param targetSessionFile target session file path (may be null for new sessions)
     */
    record SessionBeforeSwitchEvent(
        String reason,
        String targetSessionFile
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_before_switch"; }
    }

    /**
     * Fired after switching to another session.
     *
     * <p><b>Validates: Requirement 6.5</b>
     *
     * @param reason              reason for switch ("new" or "resume")
     * @param previousSessionFile previous session file path (may be null)
     */
    record SessionSwitchEvent(
        String reason,
        String previousSessionFile
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_switch"; }
    }

    /**
     * Fired before forking a session (can be cancelled).
     *
     * <p><b>Validates: Requirement 6.6</b>
     *
     * @param entryId entry ID to fork from
     */
    record SessionBeforeForkEvent(
        String entryId
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_before_fork"; }
    }

    /**
     * Fired after forking a session.
     *
     * <p><b>Validates: Requirement 6.7</b>
     *
     * @param previousSessionFile previous session file path (may be null)
     */
    record SessionForkEvent(
        String previousSessionFile
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_fork"; }
    }


    /**
     * Fired before context compaction (can be cancelled or customized).
     *
     * <p><b>Validates: Requirement 6.8</b>
     *
     * @param preparation        compaction preparation data
     * @param branchEntries      entries in the branch being compacted
     * @param customInstructions custom instructions for summarization (may be null)
     */
    record SessionBeforeCompactEvent(
        Object preparation,
        java.util.List<com.pi.coding.session.SessionEntry> branchEntries,
        String customInstructions
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_before_compact"; }
    }

    /**
     * Fired after context compaction.
     *
     * <p><b>Validates: Requirement 6.9</b>
     *
     * @param compactionEntry compaction entry that was created
     * @param fromExtension   whether the compaction was triggered by an extension
     */
    record SessionCompactEvent(
        com.pi.coding.session.CompactionEntry compactionEntry,
        boolean fromExtension
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_compact"; }
    }

    /**
     * Fired before navigating in the session tree (can be cancelled).
     *
     * <p><b>Validates: Requirement 6.10</b>
     *
     * @param preparation tree navigation preparation data
     */
    record SessionBeforeTreeEvent(
        TreePreparation preparation
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_before_tree"; }
    }

    /**
     * Fired after navigating in the session tree.
     *
     * <p><b>Validates: Requirement 6.11</b>
     *
     * @param newLeafId     new leaf entry ID (may be null)
     * @param oldLeafId     old leaf entry ID (may be null)
     * @param summaryEntry  branch summary entry if created (may be null)
     * @param fromExtension whether the navigation was triggered by an extension
     */
    record SessionTreeEvent(
        String newLeafId,
        String oldLeafId,
        com.pi.coding.session.BranchSummaryEntry summaryEntry,
        Boolean fromExtension
    ) implements ExtensionEvent {
        @Override
        public String type() { return "session_tree"; }
    }

    /**
     * Fired on process exit.
     *
     * <p><b>Validates: Requirement 6.12</b>
     */
    record SessionShutdownEvent() implements ExtensionEvent {
        @Override
        public String type() { return "session_shutdown"; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Agent Events
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fired after user submits prompt but before agent loop.
     *
     * <p><b>Validates: Requirement 6.15</b>
     *
     * @param prompt       the user's prompt text
     * @param images       attached images (may be null)
     * @param systemPrompt the current system prompt
     */
    record BeforeAgentStartEvent(
        String prompt,
        java.util.List<com.pi.ai.core.types.ImageContent> images,
        String systemPrompt
    ) implements ExtensionEvent {
        @Override
        public String type() { return "before_agent_start"; }
    }

    /**
     * Fired when an agent loop starts.
     *
     * <p><b>Validates: Requirement 6.16</b>
     */
    record AgentStartEvent() implements ExtensionEvent {
        @Override
        public String type() { return "agent_start"; }
    }

    /**
     * Fired when an agent loop ends.
     *
     * <p><b>Validates: Requirement 6.16</b>
     *
     * @param messages all new messages produced during this run
     */
    record AgentEndEvent(
        java.util.List<com.pi.agent.types.AgentMessage> messages
    ) implements ExtensionEvent {
        @Override
        public String type() { return "agent_end"; }
    }


    /**
     * Fired at the start of each turn.
     *
     * <p><b>Validates: Requirement 6.17</b>
     *
     * @param turnIndex zero-based turn index
     * @param timestamp Unix timestamp in milliseconds
     */
    record TurnStartEvent(
        int turnIndex,
        long timestamp
    ) implements ExtensionEvent {
        @Override
        public String type() { return "turn_start"; }
    }

    /**
     * Fired at the end of each turn.
     *
     * <p><b>Validates: Requirement 6.17</b>
     *
     * @param turnIndex   zero-based turn index
     * @param message     the assistant message for this turn
     * @param toolResults tool result messages produced during this turn
     */
    record TurnEndEvent(
        int turnIndex,
        com.pi.agent.types.AgentMessage message,
        java.util.List<com.pi.ai.core.types.ToolResultMessage> toolResults
    ) implements ExtensionEvent {
        @Override
        public String type() { return "turn_end"; }
    }

    /**
     * Fired when a message starts (user, assistant, or toolResult).
     *
     * <p><b>Validates: Requirement 6.18</b>
     *
     * @param message the message being added
     */
    record MessageStartEvent(
        com.pi.agent.types.AgentMessage message
    ) implements ExtensionEvent {
        @Override
        public String type() { return "message_start"; }
    }

    /**
     * Fired during assistant message streaming with token-by-token updates.
     *
     * <p><b>Validates: Requirement 6.18</b>
     *
     * @param message               the current partial assistant message
     * @param assistantMessageEvent the underlying LLM streaming event
     */
    record MessageUpdateEvent(
        com.pi.agent.types.AgentMessage message,
        com.pi.ai.core.event.AssistantMessageEvent assistantMessageEvent
    ) implements ExtensionEvent {
        @Override
        public String type() { return "message_update"; }
    }

    /**
     * Fired when a message ends.
     *
     * <p><b>Validates: Requirement 6.18</b>
     *
     * @param message the finalized message
     */
    record MessageEndEvent(
        com.pi.agent.types.AgentMessage message
    ) implements ExtensionEvent {
        @Override
        public String type() { return "message_end"; }
    }

    /**
     * Fired when a tool starts executing.
     *
     * <p><b>Validates: Requirement 6.19</b>
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName   name of the tool being executed
     * @param args       validated tool arguments
     */
    record ToolExecutionStartEvent(
        String toolCallId,
        String toolName,
        Object args
    ) implements ExtensionEvent {
        @Override
        public String type() { return "tool_execution_start"; }
    }

    /**
     * Fired during tool execution with partial/streaming output.
     *
     * <p><b>Validates: Requirement 6.19</b>
     *
     * @param toolCallId    unique identifier for this tool call
     * @param toolName      name of the tool being executed
     * @param args          validated tool arguments
     * @param partialResult partial result from the tool's onUpdate callback
     */
    record ToolExecutionUpdateEvent(
        String toolCallId,
        String toolName,
        Object args,
        Object partialResult
    ) implements ExtensionEvent {
        @Override
        public String type() { return "tool_execution_update"; }
    }

    /**
     * Fired when a tool finishes executing.
     *
     * <p><b>Validates: Requirement 6.19</b>
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName   name of the tool that was executed
     * @param result     final tool execution result
     * @param isError    whether the tool execution resulted in an error
     */
    record ToolExecutionEndEvent(
        String toolCallId,
        String toolName,
        Object result,
        boolean isError
    ) implements ExtensionEvent {
        @Override
        public String type() { return "tool_execution_end"; }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // Interception Events
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fired before each LLM call. Can modify messages.
     *
     * <p><b>Validates: Requirement 6.13</b>
     *
     * @param messages the messages to be sent to the LLM
     */
    record ContextEvent(
        java.util.List<com.pi.agent.types.AgentMessage> messages
    ) implements ExtensionEvent {
        @Override
        public String type() { return "context"; }
    }

    /**
     * Fired before a provider request is sent. Can replace the payload.
     *
     * <p><b>Validates: Requirement 6.14</b>
     *
     * @param payload the request payload to be sent
     */
    record BeforeProviderRequestEvent(
        Object payload
    ) implements ExtensionEvent {
        @Override
        public String type() { return "before_provider_request"; }
    }

    /**
     * Fired when user input is received, before agent processing.
     *
     * <p><b>Validates: Requirement 6.22</b>
     *
     * @param text   the input text
     * @param images attached images (may be null)
     * @param source where the input came from ("interactive", "rpc", or "extension")
     */
    record InputEvent(
        String text,
        java.util.List<com.pi.ai.core.types.ImageContent> images,
        String source
    ) implements ExtensionEvent {
        @Override
        public String type() { return "input"; }
    }

    /**
     * Fired before a tool executes. Can block.
     *
     * <p><b>Validates: Requirement 6.23</b>
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName   name of the tool being called
     * @param input      tool input parameters
     */
    record ToolCallEvent(
        String toolCallId,
        String toolName,
        Object input
    ) implements ExtensionEvent {
        @Override
        public String type() { return "tool_call"; }
    }

    /**
     * Fired after a tool executes. Can modify result.
     *
     * <p><b>Validates: Requirement 6.24</b>
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName   name of the tool that was executed
     * @param input      tool input parameters
     * @param content    result content blocks
     * @param details    tool-specific details (may be null)
     * @param isError    whether the tool execution resulted in an error
     */
    record ToolResultEvent(
        String toolCallId,
        String toolName,
        Object input,
        java.util.List<com.pi.ai.core.types.ContentBlock> content,
        Object details,
        boolean isError
    ) implements ExtensionEvent {
        @Override
        public String type() { return "tool_result"; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other Events
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fired when a new model is selected.
     *
     * <p><b>Validates: Requirement 6.20</b>
     *
     * @param model         the newly selected model
     * @param previousModel the previously selected model (may be null)
     * @param source        source of the selection ("set", "cycle", or "restore")
     */
    record ModelSelectEvent(
        com.pi.ai.core.types.Model model,
        com.pi.ai.core.types.Model previousModel,
        String source
    ) implements ExtensionEvent {
        @Override
        public String type() { return "model_select"; }
    }

    /**
     * Fired when user executes a bash command via ! or !! prefix.
     *
     * <p><b>Validates: Requirement 6.21</b>
     *
     * @param command            the command to execute
     * @param excludeFromContext true if !! prefix was used (excluded from LLM context)
     * @param cwd                current working directory
     */
    record UserBashEvent(
        String command,
        boolean excludeFromContext,
        String cwd
    ) implements ExtensionEvent {
        @Override
        public String type() { return "user_bash"; }
    }
}
