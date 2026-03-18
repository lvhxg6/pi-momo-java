package com.pi.coding.compaction;

import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.types.*;

import java.util.List;

/**
 * Utility methods for context compaction.
 *
 * <p>Provides token calculation, estimation, and compaction decision logic.
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3</b>
 */
public final class CompactionUtils {

    private CompactionUtils() {
        // Utility class
    }

    /**
     * Calculate total context tokens from usage data.
     *
     * <p>Uses the native totalTokens field when available, falls back to computing from components.
     *
     * <p><b>Validates: Requirement 3.1</b>
     *
     * @param usage The usage statistics from an LLM call
     * @return Total context tokens
     */
    public static int calculateContextTokens(Usage usage) {
        if (usage == null) {
            return 0;
        }
        if (usage.totalTokens() > 0) {
            return usage.totalTokens();
        }
        return usage.input() + usage.output() + usage.cacheRead() + usage.cacheWrite();
    }

    /**
     * Estimate token count for a list of messages using chars/4 heuristic.
     *
     * <p>This is conservative (overestimates tokens).
     *
     * <p><b>Validates: Requirement 3.2</b>
     *
     * @param messages The messages to estimate
     * @return Estimated token count
     */
    public static int estimateTokens(List<? extends AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (AgentMessage message : messages) {
            total += estimateTokens(message);
        }
        return total;
    }

    /**
     * Estimate token count for a single message using chars/4 heuristic.
     *
     * <p><b>Validates: Requirement 3.2</b>
     *
     * @param message The message to estimate
     * @return Estimated token count
     */
    public static int estimateTokens(AgentMessage message) {
        if (message == null) {
            return 0;
        }

        int chars = 0;
        String role = message.role();

        // Handle wrapped LLM messages
        if (message instanceof MessageAdapter adapter) {
            Message llmMessage = adapter.message();
            return estimateLlmMessageTokens(llmMessage);
        }

        // Handle custom message types by role
        switch (role) {
            case "user" -> chars = estimateUserMessageChars(message);
            case "assistant" -> chars = estimateAssistantMessageChars(message);
            case "toolResult" -> chars = estimateToolResultMessageChars(message);
            case "custom" -> chars = estimateCustomMessageChars(message);
            case "bashExecution" -> chars = estimateBashExecutionMessageChars(message);
            case "branchSummary", "compactionSummary" -> chars = estimateSummaryMessageChars(message);
            default -> chars = estimateGenericMessageChars(message);
        }

        return Math.max(1, (int) Math.ceil(chars / 4.0));
    }

    /**
     * Estimate tokens for an LLM Message.
     */
    private static int estimateLlmMessageTokens(Message message) {
        int chars = 0;

        if (message instanceof UserMessage userMsg) {
            chars = estimateUserContentChars(userMsg.content());
        } else if (message instanceof AssistantMessage assistantMsg) {
            chars = estimateAssistantContentChars(assistantMsg.getContent());
        } else if (message instanceof ToolResultMessage toolResultMsg) {
            chars = estimateToolResultContentChars(toolResultMsg.content());
        }

        return Math.max(1, (int) Math.ceil(chars / 4.0));
    }

    /**
     * Estimate character count for user message content.
     */
    private static int estimateUserContentChars(Object content) {
        if (content instanceof String str) {
            return str.length();
        } else if (content instanceof List<?> blocks) {
            int chars = 0;
            for (Object block : blocks) {
                if (block instanceof TextContent text) {
                    chars += text.text().length();
                } else if (block instanceof UserContentBlock ucb) {
                    chars += estimateUserContentBlockChars(ucb);
                }
            }
            return chars;
        }
        return content != null ? content.toString().length() : 0;
    }

    /**
     * Estimate character count for a user content block.
     */
    private static int estimateUserContentBlockChars(UserContentBlock block) {
        if (block instanceof TextContent text) {
            return text.text().length();
        } else if (block instanceof ImageContent) {
            // Estimate images as ~4800 chars (1200 tokens)
            return 4800;
        }
        return 0;
    }

    /**
     * Estimate character count for assistant message content.
     */
    private static int estimateAssistantContentChars(List<AssistantContentBlock> content) {
        if (content == null) {
            return 0;
        }
        int chars = 0;
        for (AssistantContentBlock block : content) {
            if (block instanceof TextContent text) {
                chars += text.text().length();
            } else if (block instanceof ThinkingContent thinking) {
                chars += thinking.thinking().length();
            } else if (block instanceof ToolCall toolCall) {
                chars += toolCall.name().length();
                if (toolCall.arguments() != null) {
                    chars += toolCall.arguments().toString().length();
                }
            }
        }
        return chars;
    }

    /**
     * Estimate character count for tool result message content.
     */
    private static int estimateToolResultContentChars(List<UserContentBlock> content) {
        if (content == null) {
            return 0;
        }
        int chars = 0;
        for (UserContentBlock block : content) {
            if (block instanceof TextContent text) {
                chars += text.text().length();
            } else if (block instanceof ImageContent) {
                chars += 4800; // Estimate images as ~4800 chars
            }
        }
        return chars;
    }

    /**
     * Estimate character count for user messages (non-wrapped).
     */
    private static int estimateUserMessageChars(AgentMessage message) {
        // Try to extract content via reflection or toString
        return message.toString().length();
    }

    /**
     * Estimate character count for assistant messages (non-wrapped).
     */
    private static int estimateAssistantMessageChars(AgentMessage message) {
        return message.toString().length();
    }

    /**
     * Estimate character count for tool result messages (non-wrapped).
     */
    private static int estimateToolResultMessageChars(AgentMessage message) {
        return message.toString().length();
    }

    /**
     * Estimate character count for custom messages.
     */
    private static int estimateCustomMessageChars(AgentMessage message) {
        return message.toString().length();
    }

    /**
     * Estimate character count for bash execution messages.
     */
    private static int estimateBashExecutionMessageChars(AgentMessage message) {
        // BashExecutionMessage has command and output fields
        return message.toString().length();
    }

    /**
     * Estimate character count for summary messages.
     */
    private static int estimateSummaryMessageChars(AgentMessage message) {
        return message.toString().length();
    }

    /**
     * Estimate character count for generic messages.
     */
    private static int estimateGenericMessageChars(AgentMessage message) {
        return message.toString().length();
    }

    /**
     * Check if compaction should trigger based on context usage.
     *
     * <p><b>Validates: Requirement 3.3</b>
     *
     * @param contextTokens Current context token count
     * @param contextWindow Model's context window size
     * @param reserveTokens Tokens to reserve for response
     * @return true if compaction should be triggered
     */
    public static boolean shouldCompact(int contextTokens, int contextWindow, int reserveTokens) {
        if (contextWindow <= 0) {
            return false;
        }
        return contextTokens > contextWindow - reserveTokens;
    }

    /**
     * Get usage from an assistant message if available.
     *
     * <p>Skips aborted and error messages as they don't have valid usage data.
     *
     * @param message The agent message to check
     * @return Usage if available, null otherwise
     */
    public static Usage getAssistantUsage(AgentMessage message) {
        if (message == null) {
            return null;
        }

        if (!"assistant".equals(message.role())) {
            return null;
        }

        // Handle wrapped assistant messages
        if (message instanceof MessageAdapter adapter) {
            Message llmMessage = adapter.message();
            if (llmMessage instanceof AssistantMessage assistantMsg) {
                StopReason stopReason = assistantMsg.getStopReason();
                if (stopReason == StopReason.ABORTED || stopReason == StopReason.ERROR) {
                    return null;
                }
                return assistantMsg.getUsage();
            }
        }

        return null;
    }

    /**
     * Find the last non-aborted assistant message usage from a list of messages.
     *
     * @param messages The messages to search
     * @return Usage if found, null otherwise
     */
    public static Usage getLastAssistantUsage(List<? extends AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            Usage usage = getAssistantUsage(messages.get(i));
            if (usage != null) {
                return usage;
            }
        }

        return null;
    }
}
