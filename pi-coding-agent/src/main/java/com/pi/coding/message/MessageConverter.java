package com.pi.coding.message;

import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.types.Message;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.UserContentBlock;
import com.pi.ai.core.types.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts AgentMessages (including custom types) to LLM-compatible Messages.
 *
 * <p>This is used by:
 * <ul>
 *   <li>Agent's convertToLlm option (for prompt calls and queued messages)</li>
 *   <li>Compaction's generateSummary (for summarization)</li>
 *   <li>Custom extensions and tools</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 23.1-23.8</b>
 */
public final class MessageConverter {

    /** Prefix for compaction summary messages in LLM context. */
    public static final String COMPACTION_SUMMARY_PREFIX =
            "The conversation history before this point was compacted into the following summary:\n\n<summary>\n";

    /** Suffix for compaction summary messages in LLM context. */
    public static final String COMPACTION_SUMMARY_SUFFIX = "\n</summary>";

    /** Prefix for branch summary messages in LLM context. */
    public static final String BRANCH_SUMMARY_PREFIX =
            "The following is a summary of a branch that this conversation came back from:\n\n<summary>\n";

    /** Suffix for branch summary messages in LLM context. */
    public static final String BRANCH_SUMMARY_SUFFIX = "</summary>";

    private MessageConverter() {
        // Utility class
    }

    /**
     * Convert a list of AgentMessages to LLM-compatible Messages.
     *
     * <p>Handles standard LLM messages (user, assistant, toolResult) as pass-through,
     * and converts custom message types:
     * <ul>
     *   <li>{@code bashExecution} → user message with formatted command output</li>
     *   <li>{@code custom} → user message with content</li>
     *   <li>{@code branchSummary} → user message with summary prefix/suffix</li>
     *   <li>{@code compactionSummary} → user message with summary prefix/suffix</li>
     * </ul>
     *
     * @param messages the agent messages to convert
     * @return list of LLM-compatible messages (filtered, never null)
     */
    public static List<Message> convertToLlm(List<AgentMessage> messages) {
        List<Message> result = new ArrayList<>();
        for (AgentMessage m : messages) {
            Message converted = convertSingle(m);
            if (converted != null) {
                result.add(converted);
            }
        }
        return result;
    }

    /**
     * Convert a single AgentMessage to an LLM Message.
     *
     * @param m the agent message
     * @return the converted message, or null if the message should be filtered out
     */
    static Message convertSingle(AgentMessage m) {
        // Standard LLM messages wrapped in MessageAdapter - pass through
        if (m instanceof MessageAdapter adapter) {
            return adapter.message();
        }

        // Custom message types
        String role = m.role();
        return switch (role) {
            case "bashExecution" -> convertBashExecution(m);
            case "custom" -> convertCustom(m);
            case "branchSummary" -> convertBranchSummary(m);
            case "compactionSummary" -> convertCompactionSummary(m);
            case "user", "assistant", "toolResult" -> {
                // Non-adapter standard messages - should not normally happen,
                // but handle gracefully by creating a user message with toString
                yield createTextUserMessage(m.toString(), m.timestamp());
            }
            default -> null; // Unknown types are filtered out
        };
    }

    /**
     * Convert a BashExecutionMessage to a user message.
     * Messages with excludeFromContext=true are filtered out.
     */
    private static Message convertBashExecution(AgentMessage m) {
        if (m instanceof BashExecutionMessage bash) {
            // Skip messages excluded from context (!! prefix)
            if (Boolean.TRUE.equals(bash.excludeFromContext())) {
                return null;
            }
            return createTextUserMessage(bash.toText(), bash.timestamp());
        }
        // Fallback for non-typed bash execution messages
        return createTextUserMessage(m.toString(), m.timestamp());
    }

    /**
     * Convert a CustomMessage to a user message.
     */
    @SuppressWarnings("unchecked")
    private static Message convertCustom(AgentMessage m) {
        if (m instanceof CustomMessage custom) {
            Object content = custom.content();
            if (content instanceof String text) {
                return createTextUserMessage(text, custom.timestamp());
            }
            if (content instanceof List<?> blocks) {
                // Content blocks - pass through as user message content
                return new UserMessage("user", blocks, custom.timestamp());
            }
            // Fallback
            return createTextUserMessage(String.valueOf(content), custom.timestamp());
        }
        // Fallback for non-typed custom messages
        return createTextUserMessage(m.toString(), m.timestamp());
    }

    /**
     * Convert a BranchSummaryMessage to a user message with summary wrapping.
     */
    private static Message convertBranchSummary(AgentMessage m) {
        String summary;
        if (m instanceof BranchSummaryMessage bsm) {
            summary = bsm.summary();
        } else {
            summary = m.toString();
        }
        return createTextUserMessage(
                BRANCH_SUMMARY_PREFIX + summary + BRANCH_SUMMARY_SUFFIX,
                m.timestamp()
        );
    }

    /**
     * Convert a CompactionSummaryMessage to a user message with summary wrapping.
     */
    private static Message convertCompactionSummary(AgentMessage m) {
        String summary;
        if (m instanceof CompactionSummaryMessage csm) {
            summary = csm.summary();
        } else {
            summary = m.toString();
        }
        return createTextUserMessage(
                COMPACTION_SUMMARY_PREFIX + summary + COMPACTION_SUMMARY_SUFFIX,
                m.timestamp()
        );
    }

    /**
     * Create a UserMessage with a single text content block.
     */
    private static UserMessage createTextUserMessage(String text, long timestamp) {
        return new UserMessage(
                "user",
                List.of(new TextContent(text)),
                timestamp
        );
    }
}
