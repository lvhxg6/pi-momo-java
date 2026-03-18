package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.agent.types.AgentMessage;
import com.pi.ai.core.types.ContentBlock;
import com.pi.ai.core.types.ImageContent;
import com.pi.coding.compaction.CompactionResult;

import java.util.List;

/**
 * Event result types returned by extension event handlers.
 *
 * <p>These records define the possible return values from event handlers,
 * allowing extensions to modify agent behavior or cancel operations.
 */
public final class EventResult {

    private EventResult() {
        // Utility class
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Resource Event Results
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Result from resources_discover event handler.
     *
     * @param skillPaths  additional skill paths to load
     * @param promptPaths additional prompt template paths to load
     * @param themePaths  additional theme paths to load
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourcesDiscoverResult(
        @JsonProperty("skillPaths") List<String> skillPaths,
        @JsonProperty("promptPaths") List<String> promptPaths,
        @JsonProperty("themePaths") List<String> themePaths
    ) { }

    /**
     * Result from session_directory event handler.
     *
     * @param sessionDir custom session directory path
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionDirectoryResult(
        @JsonProperty("sessionDir") String sessionDir
    ) { }

    // ══════════════════════════════════════════════════════════════════════════
    // Session Event Results
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Result from session_before_switch event handler.
     *
     * @param cancel whether to cancel the switch operation
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionBeforeSwitchResult(
        @JsonProperty("cancel") Boolean cancel
    ) { }

    /**
     * Result from session_before_fork event handler.
     *
     * @param cancel                  whether to cancel the fork operation
     * @param skipConversationRestore whether to skip restoring conversation
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionBeforeForkResult(
        @JsonProperty("cancel") Boolean cancel,
        @JsonProperty("skipConversationRestore") Boolean skipConversationRestore
    ) { }

    /**
     * Result from session_before_compact event handler.
     *
     * @param cancel     whether to cancel the compaction
     * @param compaction custom compaction result to use instead
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionBeforeCompactResult(
        @JsonProperty("cancel") Boolean cancel,
        @JsonProperty("compaction") CompactionResult compaction
    ) { }

    /**
     * Result from session_before_tree event handler.
     *
     * @param cancel              whether to cancel the navigation
     * @param summary             custom summary to use
     * @param customInstructions  override custom instructions for summarization
     * @param replaceInstructions override whether customInstructions replaces the default prompt
     * @param label               override label to attach to the branch summary entry
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionBeforeTreeResult(
        @JsonProperty("cancel") Boolean cancel,
        @JsonProperty("summary") SummaryOverride summary,
        @JsonProperty("customInstructions") String customInstructions,
        @JsonProperty("replaceInstructions") Boolean replaceInstructions,
        @JsonProperty("label") String label
    ) { }

    /**
     * Custom summary override for tree navigation.
     *
     * @param summary summary text
     * @param details additional details (may be null)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryOverride(
        @JsonProperty("summary") String summary,
        @JsonProperty("details") Object details
    ) { }

    // ══════════════════════════════════════════════════════════════════════════
    // Agent Event Results
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Result from before_agent_start event handler.
     *
     * @param message      custom message to inject
     * @param systemPrompt replacement system prompt for this turn
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BeforeAgentStartResult(
        @JsonProperty("message") CustomMessageData message,
        @JsonProperty("systemPrompt") String systemPrompt
    ) { }

    /**
     * Custom message data for injection.
     *
     * @param customType custom type identifier
     * @param content    message content
     * @param display    whether to display in UI
     * @param details    additional details (may be null)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomMessageData(
        @JsonProperty("customType") String customType,
        @JsonProperty("content") Object content,
        @JsonProperty("display") boolean display,
        @JsonProperty("details") Object details
    ) { }

    // ══════════════════════════════════════════════════════════════════════════
    // Interception Event Results
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Result from context event handler.
     *
     * @param messages modified messages to use
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextEventResult(
        @JsonProperty("messages") List<AgentMessage> messages
    ) { }

    /**
     * Result from input event handler.
     */
    public sealed interface InputEventResult permits
            InputEventResult.Continue,
            InputEventResult.Transform,
            InputEventResult.Handled {

        /** Continue with original input. */
        record Continue() implements InputEventResult { }

        /**
         * Transform the input.
         *
         * @param text   transformed text
         * @param images transformed images (may be null)
         */
        record Transform(String text, List<ImageContent> images) implements InputEventResult { }

        /** Input was fully handled by the extension. */
        record Handled() implements InputEventResult { }
    }

    /**
     * Result from tool_call event handler.
     *
     * @param block  whether to block the tool execution
     * @param reason reason for blocking (may be null)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCallEventResult(
        @JsonProperty("block") Boolean block,
        @JsonProperty("reason") String reason
    ) { }

    /**
     * Result from tool_result event handler.
     *
     * @param content modified content blocks
     * @param details modified details
     * @param isError modified error status
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolResultEventResult(
        @JsonProperty("content") List<ContentBlock> content,
        @JsonProperty("details") Object details,
        @JsonProperty("isError") Boolean isError
    ) { }

    /**
     * Result from user_bash event handler.
     *
     * @param operations custom operations to use for execution
     * @param result     full replacement result (extension handled execution)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserBashEventResult(
        @JsonProperty("operations") Object operations,
        @JsonProperty("result") Object result
    ) { }
}
