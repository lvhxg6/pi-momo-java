package com.pi.coding.compaction;

import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generates summaries for compaction using LLM.
 *
 * <p>Uses structured format (Goal, Progress, Key Decisions, Next Steps).
 * Supports update mode (merging new information into existing summary).
 *
 * <p><b>Validates: Requirements 3.9, 3.10, 3.11, 3.13, 3.14, 3.15</b>
 */
public final class SummaryGenerator {

    private SummaryGenerator() {
        // Utility class
    }

    /**
     * System prompt for summarization.
     */
    public static final String SUMMARIZATION_SYSTEM_PROMPT = """
            You are a context summarization assistant. Your task is to read a conversation between a user and an AI coding assistant, then produce a structured summary following the exact format specified.

            Do NOT continue the conversation. Do NOT respond to any questions in the conversation. ONLY output the structured summary.""";

    /**
     * Prompt for initial summarization.
     */
    public static final String SUMMARIZATION_PROMPT = """
            The messages above are a conversation to summarize. Create a structured context checkpoint summary that another LLM will use to continue the work.

            Use this EXACT format:

            ## Goal
            [What is the user trying to accomplish? Can be multiple items if the session covers different tasks.]

            ## Constraints & Preferences
            - [Any constraints, preferences, or requirements mentioned by user]
            - [Or "(none)" if none were mentioned]

            ## Progress
            ### Done
            - [x] [Completed tasks/changes]

            ### In Progress
            - [ ] [Current work]

            ### Blocked
            - [Issues preventing progress, if any]

            ## Key Decisions
            - **[Decision]**: [Brief rationale]

            ## Next Steps
            1. [Ordered list of what should happen next]

            ## Critical Context
            - [Any data, examples, or references needed to continue]
            - [Or "(none)" if not applicable]

            Keep each section concise. Preserve exact file paths, function names, and error messages.""";

    /**
     * Prompt for updating an existing summary.
     */
    public static final String UPDATE_SUMMARIZATION_PROMPT = """
            The messages above are NEW conversation messages to incorporate into the existing summary provided in <previous-summary> tags.

            Update the existing structured summary with new information. RULES:
            - PRESERVE all existing information from the previous summary
            - ADD new progress, decisions, and context from the new messages
            - UPDATE the Progress section: move items from "In Progress" to "Done" when completed
            - UPDATE "Next Steps" based on what was accomplished
            - PRESERVE exact file paths, function names, and error messages
            - If something is no longer relevant, you may remove it

            Use this EXACT format:

            ## Goal
            [Preserve existing goals, add new ones if the task expanded]

            ## Constraints & Preferences
            - [Preserve existing, add new ones discovered]

            ## Progress
            ### Done
            - [x] [Include previously done items AND newly completed items]

            ### In Progress
            - [ ] [Current work - update based on progress]

            ### Blocked
            - [Current blockers - remove if resolved]

            ## Key Decisions
            - **[Decision]**: [Brief rationale] (preserve all previous, add new)

            ## Next Steps
            1. [Update based on current state]

            ## Critical Context
            - [Preserve important context, add new if needed]

            Keep each section concise. Preserve exact file paths, function names, and error messages.""";

    /**
     * Prompt for turn prefix summarization.
     */
    public static final String TURN_PREFIX_SUMMARIZATION_PROMPT = """
            This is the PREFIX of a turn that was too large to keep. The SUFFIX (recent work) is retained.

            Summarize the prefix to provide context for the retained suffix:

            ## Original Request
            [What did the user ask for in this turn?]

            ## Early Progress
            - [Key decisions and work done in the prefix]

            ## Context for Suffix
            - [Information needed to understand the retained recent work]

            Be concise. Focus on what's needed to understand the kept suffix.""";

    /**
     * Maximum characters for a tool result in serialized summaries.
     */
    private static final int TOOL_RESULT_MAX_CHARS = 2000;

    /**
     * Truncate text to a maximum character length for summarization.
     */
    static String truncateForSummary(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        int truncatedChars = text.length() - maxChars;
        return text.substring(0, maxChars) + "\n\n[... " + truncatedChars + " more characters truncated]";
    }

    /**
     * Serialize LLM messages to text for summarization.
     *
     * <p>This prevents the model from treating it as a conversation to continue.
     * Tool results are truncated to keep the summarization request within
     * reasonable token budgets.
     *
     * @param messages The messages to serialize
     * @return Serialized conversation text
     */
    public static String serializeConversation(List<? extends AgentMessage> messages) {
        List<String> parts = new ArrayList<>();

        for (AgentMessage msg : messages) {
            String serialized = serializeMessage(msg);
            if (serialized != null && !serialized.isEmpty()) {
                parts.add(serialized);
            }
        }

        return String.join("\n\n", parts);
    }

    /**
     * Serialize a single message to text.
     */
    private static String serializeMessage(AgentMessage msg) {
        if (msg == null) {
            return null;
        }

        // Handle wrapped LLM messages
        if (msg instanceof MessageAdapter adapter) {
            return serializeLlmMessage(adapter.message());
        }

        // Handle custom message types by role
        String role = msg.role();
        return switch (role) {
            case "user" -> "[User]: " + msg.toString();
            case "assistant" -> "[Assistant]: " + msg.toString();
            case "toolResult" -> "[Tool result]: " + truncateForSummary(msg.toString(), TOOL_RESULT_MAX_CHARS);
            case "custom" -> "[Custom]: " + msg.toString();
            case "bashExecution" -> "[Bash]: " + msg.toString();
            case "branchSummary", "compactionSummary" -> "[Summary]: " + msg.toString();
            default -> "[" + role + "]: " + msg.toString();
        };
    }

    /**
     * Serialize an LLM Message to text.
     */
    private static String serializeLlmMessage(Message msg) {
        if (msg instanceof UserMessage userMsg) {
            return serializeUserMessage(userMsg);
        } else if (msg instanceof AssistantMessage assistantMsg) {
            return serializeAssistantMessage(assistantMsg);
        } else if (msg instanceof ToolResultMessage toolResultMsg) {
            return serializeToolResultMessage(toolResultMsg);
        }
        return null;
    }

    /**
     * Serialize a user message.
     */
    private static String serializeUserMessage(UserMessage msg) {
        Object content = msg.content();
        String text;

        if (content instanceof String str) {
            text = str;
        } else if (content instanceof List<?> blocks) {
            StringBuilder sb = new StringBuilder();
            for (Object block : blocks) {
                if (block instanceof TextContent tc) {
                    sb.append(tc.text());
                }
            }
            text = sb.toString();
        } else {
            text = content != null ? content.toString() : "";
        }

        if (text.isEmpty()) {
            return null;
        }
        return "[User]: " + text;
    }

    /**
     * Serialize an assistant message.
     */
    private static String serializeAssistantMessage(AssistantMessage msg) {
        List<String> parts = new ArrayList<>();
        List<String> textParts = new ArrayList<>();
        List<String> thinkingParts = new ArrayList<>();
        List<String> toolCalls = new ArrayList<>();

        List<AssistantContentBlock> content = msg.getContent();
        if (content != null) {
            for (AssistantContentBlock block : content) {
                if (block instanceof TextContent tc) {
                    textParts.add(tc.text());
                } else if (block instanceof ThinkingContent thinking) {
                    thinkingParts.add(thinking.thinking());
                } else if (block instanceof ToolCall toolCall) {
                    Map<String, Object> args = toolCall.arguments();
                    StringBuilder argsStr = new StringBuilder();
                    if (args != null) {
                        args.forEach((k, v) -> {
                            if (argsStr.length() > 0) argsStr.append(", ");
                            argsStr.append(k).append("=").append(v);
                        });
                    }
                    toolCalls.add(toolCall.name() + "(" + argsStr + ")");
                }
            }
        }

        if (!thinkingParts.isEmpty()) {
            parts.add("[Assistant thinking]: " + String.join("\n", thinkingParts));
        }
        if (!textParts.isEmpty()) {
            parts.add("[Assistant]: " + String.join("\n", textParts));
        }
        if (!toolCalls.isEmpty()) {
            parts.add("[Assistant tool calls]: " + String.join("; ", toolCalls));
        }

        return String.join("\n\n", parts);
    }

    /**
     * Serialize a tool result message.
     */
    private static String serializeToolResultMessage(ToolResultMessage msg) {
        List<UserContentBlock> content = msg.content();
        if (content == null || content.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (UserContentBlock block : content) {
            if (block instanceof TextContent tc) {
                sb.append(tc.text());
            }
        }

        String text = sb.toString();
        if (text.isEmpty()) {
            return null;
        }

        return "[Tool result]: " + truncateForSummary(text, TOOL_RESULT_MAX_CHARS);
    }

    /**
     * Build the prompt text for summarization.
     *
     * @param messages        The messages to summarize
     * @param previousSummary Previous summary for update mode (nullable)
     * @param customInstructions Additional focus instructions (nullable)
     * @return The prompt text
     */
    public static String buildSummarizationPrompt(
            List<? extends AgentMessage> messages,
            String previousSummary,
            String customInstructions
    ) {
        // Serialize conversation to text
        String conversationText = serializeConversation(messages);

        // Use update prompt if we have a previous summary
        String basePrompt = previousSummary != null ? UPDATE_SUMMARIZATION_PROMPT : SUMMARIZATION_PROMPT;

        if (customInstructions != null && !customInstructions.isEmpty()) {
            basePrompt = basePrompt + "\n\nAdditional focus: " + customInstructions;
        }

        // Build the prompt with conversation wrapped in tags
        StringBuilder promptText = new StringBuilder();
        promptText.append("<conversation>\n").append(conversationText).append("\n</conversation>\n\n");

        if (previousSummary != null) {
            promptText.append("<previous-summary>\n").append(previousSummary).append("\n</previous-summary>\n\n");
        }

        promptText.append(basePrompt);

        return promptText.toString();
    }

    /**
     * Build the prompt text for turn prefix summarization.
     *
     * @param messages The turn prefix messages to summarize
     * @return The prompt text
     */
    public static String buildTurnPrefixPrompt(List<? extends AgentMessage> messages) {
        String conversationText = serializeConversation(messages);
        return "<conversation>\n" + conversationText + "\n</conversation>\n\n" + TURN_PREFIX_SUMMARIZATION_PROMPT;
    }

    /**
     * Generate a summary of the conversation.
     *
     * <p>This is a placeholder that returns a simple summary.
     * The actual implementation would call an LLM.
     *
     * <p><b>Validates: Requirements 3.9, 3.10, 3.11, 3.14, 3.15</b>
     *
     * @param messages           Messages to summarize
     * @param previousSummary    Previous summary for update mode (nullable)
     * @param fileOps            File operations to include
     * @param customInstructions Additional focus instructions (nullable)
     * @param signal             Cancellation signal (nullable)
     * @return CompletableFuture with the generated summary
     */
    public static CompletableFuture<String> generateSummary(
            List<? extends AgentMessage> messages,
            String previousSummary,
            FileOperations fileOps,
            String customInstructions,
            CancellationSignal signal
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // Check for cancellation
            if (signal != null && signal.isCancelled()) {
                throw new RuntimeException("Summary generation cancelled");
            }

            // Build the prompt (for future LLM call)
            String prompt = buildSummarizationPrompt(messages, previousSummary, customInstructions);

            // For now, generate a simple summary based on the messages
            // In a real implementation, this would call an LLM
            StringBuilder summary = new StringBuilder();

            summary.append("## Goal\n");
            summary.append("[Session context preserved]\n\n");

            summary.append("## Progress\n");
            summary.append("### Done\n");
            summary.append("- [x] Previous conversation summarized\n\n");

            summary.append("## Key Decisions\n");
            summary.append("- **Context compacted**: Session history summarized to fit context window\n\n");

            summary.append("## Next Steps\n");
            summary.append("1. Continue with the current task\n");

            // Append file operations
            if (fileOps != null && !fileOps.isEmpty()) {
                Map<String, List<String>> fileLists = Compaction.computeFileLists(fileOps);
                String fileOpsStr = Compaction.formatFileOperations(
                        fileLists.get("readFiles"),
                        fileLists.get("modifiedFiles")
                );
                summary.append(fileOpsStr);
            }

            return summary.toString();
        });
    }

    /**
     * Generate a summary for a turn prefix (when splitting a turn).
     *
     * @param messages The turn prefix messages
     * @param signal   Cancellation signal (nullable)
     * @return CompletableFuture with the generated summary
     */
    public static CompletableFuture<String> generateTurnPrefixSummary(
            List<? extends AgentMessage> messages,
            CancellationSignal signal
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // Check for cancellation
            if (signal != null && signal.isCancelled()) {
                throw new RuntimeException("Turn prefix summary generation cancelled");
            }

            // Build the prompt (for future LLM call)
            String prompt = buildTurnPrefixPrompt(messages);

            // For now, generate a simple summary
            // In a real implementation, this would call an LLM
            StringBuilder summary = new StringBuilder();

            summary.append("## Original Request\n");
            summary.append("[Turn prefix context preserved]\n\n");

            summary.append("## Early Progress\n");
            summary.append("- Initial work in this turn summarized\n\n");

            summary.append("## Context for Suffix\n");
            summary.append("- Continue with the retained recent work\n");

            return summary.toString();
        });
    }
}
