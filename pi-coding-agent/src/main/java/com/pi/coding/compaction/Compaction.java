package com.pi.coding.compaction;

import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.types.*;
import com.pi.coding.session.*;

import java.util.*;

/**
 * Context compaction for long sessions.
 *
 * <p>Provides pure functions for compaction logic. The session manager handles I/O,
 * and after compaction the session is reloaded.
 *
 * <p><b>Validates: Requirements 3.4-3.15</b>
 */
public final class Compaction {

    private Compaction() {
        // Utility class
    }

    // =========================================================================
    // Cut Point Detection (Task 5.2)
    // =========================================================================

    /**
     * Find valid cut points: indices of user, assistant, custom, or bashExecution messages.
     *
     * <p>Never cut at tool results (they must follow their tool call).
     * When we cut at an assistant message with tool calls, its tool results follow it
     * and will be kept.
     *
     * <p><b>Validates: Requirements 3.4, 3.5</b>
     *
     * @param entries    The session entries to search
     * @param startIndex Start index (inclusive)
     * @param endIndex   End index (exclusive)
     * @return List of valid cut point indices
     */
    static List<Integer> findValidCutPoints(List<SessionEntry> entries, int startIndex, int endIndex) {
        List<Integer> cutPoints = new ArrayList<>();

        for (int i = startIndex; i < endIndex; i++) {
            SessionEntry entry = entries.get(i);

            if (entry instanceof SessionMessageEntry sme) {
                String role = sme.message().role();
                // Valid cut points: user, assistant, custom, bashExecution, branchSummary, compactionSummary
                // Never cut at toolResult
                if (!"toolResult".equals(role)) {
                    cutPoints.add(i);
                }
            } else if (entry instanceof BranchSummaryEntry<?> || entry instanceof CustomMessageEntry<?>) {
                // branch_summary and custom_message are user-role messages, valid cut points
                cutPoints.add(i);
            }
            // Skip other entry types: ThinkingLevelChange, ModelChange, Compaction, Custom, Label, SessionInfo
        }

        return cutPoints;
    }

    /**
     * Find the user message (or bashExecution) that starts the turn containing the given entry index.
     *
     * <p>Returns -1 if no turn start found before the index.
     * BashExecutionMessage is treated like a user message for turn boundaries.
     *
     * <p><b>Validates: Requirement 3.7</b>
     *
     * @param entries    The session entries
     * @param entryIndex The entry index to search from
     * @param startIndex The minimum index to search
     * @return Index of turn start, or -1 if not found
     */
    public static int findTurnStartIndex(List<SessionEntry> entries, int entryIndex, int startIndex) {
        for (int i = entryIndex; i >= startIndex; i--) {
            SessionEntry entry = entries.get(i);

            // branch_summary and custom_message are user-role messages, can start a turn
            if (entry instanceof BranchSummaryEntry<?> || entry instanceof CustomMessageEntry<?>) {
                return i;
            }

            if (entry instanceof SessionMessageEntry sme) {
                String role = sme.message().role();
                if ("user".equals(role) || "bashExecution".equals(role)) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * Find the cut point in session entries that keeps approximately keepRecentTokens.
     *
     * <p>Algorithm: Walk backwards from newest, accumulating estimated message sizes.
     * Stop when we've accumulated >= keepRecentTokens. Cut at that point.
     *
     * <p>Can cut at user OR assistant messages (never tool results). When cutting at an
     * assistant message with tool calls, its tool results come after and will be kept.
     *
     * <p><b>Validates: Requirements 3.4, 3.5, 3.6, 3.7</b>
     *
     * @param entries          The session entries
     * @param startIndex       Start index (inclusive)
     * @param endIndex         End index (exclusive)
     * @param keepRecentTokens Approximate tokens to keep
     * @return CutPointResult with cut point information
     */
    public static CutPointResult findCutPoint(
            List<SessionEntry> entries,
            int startIndex,
            int endIndex,
            int keepRecentTokens
    ) {
        List<Integer> cutPoints = findValidCutPoints(entries, startIndex, endIndex);

        if (cutPoints.isEmpty()) {
            return CutPointResult.noValidCutPoint(startIndex);
        }

        // Walk backwards from newest, accumulating estimated message sizes
        int accumulatedTokens = 0;
        int cutIndex = cutPoints.get(0); // Default: keep from first valid cut point

        for (int i = endIndex - 1; i >= startIndex; i--) {
            SessionEntry entry = entries.get(i);

            if (!(entry instanceof SessionMessageEntry sme)) {
                continue;
            }

            // Estimate this message's size
            int messageTokens = CompactionUtils.estimateTokens(sme.message());
            accumulatedTokens += messageTokens;

            // Check if we've exceeded the budget
            if (accumulatedTokens >= keepRecentTokens) {
                // Find the closest valid cut point at or after this entry
                for (int c = 0; c < cutPoints.size(); c++) {
                    if (cutPoints.get(c) >= i) {
                        cutIndex = cutPoints.get(c);
                        break;
                    }
                }
                break;
            }
        }

        // Scan backwards from cutIndex to include any non-message entries (settings changes, etc.)
        while (cutIndex > startIndex) {
            SessionEntry prevEntry = entries.get(cutIndex - 1);

            // Stop at compaction boundaries
            if (prevEntry instanceof CompactionEntry<?>) {
                break;
            }

            // Stop if we hit any message
            if (prevEntry instanceof SessionMessageEntry) {
                break;
            }

            // Include this non-message entry
            cutIndex--;
        }

        // Determine if this is a split turn
        SessionEntry cutEntry = entries.get(cutIndex);
        boolean isUserMessage = false;

        if (cutEntry instanceof SessionMessageEntry sme) {
            isUserMessage = "user".equals(sme.message().role());
        } else if (cutEntry instanceof BranchSummaryEntry<?> || cutEntry instanceof CustomMessageEntry<?>) {
            // These are user-role messages
            isUserMessage = true;
        }

        int turnStartIndex = isUserMessage ? -1 : findTurnStartIndex(entries, cutIndex, startIndex);

        return new CutPointResult(
                cutIndex,
                turnStartIndex,
                !isUserMessage && turnStartIndex != -1
        );
    }

    /**
     * Simplified findCutPoint for a list of messages (not session entries).
     *
     * <p>This is used when we have a flat list of AgentMessages rather than SessionEntries.
     *
     * @param messages         The messages to search
     * @param keepRecentTokens Approximate tokens to keep
     * @return Index of first message to keep
     */
    public static int findCutPointInMessages(List<? extends AgentMessage> messages, int keepRecentTokens) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        // Find valid cut points (not toolResult)
        List<Integer> cutPoints = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            String role = messages.get(i).role();
            if (!"toolResult".equals(role)) {
                cutPoints.add(i);
            }
        }

        if (cutPoints.isEmpty()) {
            return 0;
        }

        // Walk backwards accumulating tokens
        int accumulatedTokens = 0;
        int cutIndex = cutPoints.get(0);

        for (int i = messages.size() - 1; i >= 0; i--) {
            int messageTokens = CompactionUtils.estimateTokens(messages.get(i));
            accumulatedTokens += messageTokens;

            if (accumulatedTokens >= keepRecentTokens) {
                // Find closest valid cut point at or after this index
                for (int c = 0; c < cutPoints.size(); c++) {
                    if (cutPoints.get(c) >= i) {
                        cutIndex = cutPoints.get(c);
                        break;
                    }
                }
                break;
            }
        }

        return cutIndex;
    }

    // =========================================================================
    // File Operation Extraction (Task 5.3)
    // =========================================================================

    /**
     * Extract file operations from tool calls in an assistant message.
     *
     * <p><b>Validates: Requirement 3.12</b>
     *
     * @param message The message to extract from
     * @param fileOps The file operations tracker to update
     */
    public static void extractFileOpsFromMessage(AgentMessage message, FileOperations fileOps) {
        if (message == null || fileOps == null) {
            return;
        }

        if (!"assistant".equals(message.role())) {
            return;
        }

        // Handle wrapped assistant messages
        if (message instanceof MessageAdapter adapter) {
            Message llmMessage = adapter.message();
            if (llmMessage instanceof AssistantMessage assistantMsg) {
                extractFileOpsFromAssistantMessage(assistantMsg, fileOps);
            }
        }
    }

    /**
     * Extract file operations from an AssistantMessage.
     */
    private static void extractFileOpsFromAssistantMessage(AssistantMessage message, FileOperations fileOps) {
        List<AssistantContentBlock> content = message.getContent();
        if (content == null) {
            return;
        }

        for (AssistantContentBlock block : content) {
            if (block instanceof ToolCall toolCall) {
                extractFileOpsFromToolCall(toolCall, fileOps);
            }
        }
    }

    /**
     * Extract file operations from a tool call.
     */
    private static void extractFileOpsFromToolCall(ToolCall toolCall, FileOperations fileOps) {
        Map<String, Object> args = toolCall.arguments();
        if (args == null) {
            return;
        }

        Object pathObj = args.get("path");
        if (!(pathObj instanceof String path) || path.isEmpty()) {
            return;
        }

        switch (toolCall.name()) {
            case "read" -> fileOps.addRead(path);
            case "write" -> fileOps.addWritten(path);
            case "edit" -> fileOps.addEdited(path);
        }
    }

    /**
     * Extract file operations from a list of messages.
     *
     * @param messages The messages to extract from
     * @return FileOperations tracker with extracted operations
     */
    public static FileOperations extractFileOperations(List<? extends AgentMessage> messages) {
        FileOperations fileOps = new FileOperations();
        if (messages != null) {
            for (AgentMessage message : messages) {
                extractFileOpsFromMessage(message, fileOps);
            }
        }
        return fileOps;
    }

    /**
     * Compute final file lists from file operations.
     *
     * <p>Returns readFiles (files only read, not modified) and modifiedFiles.
     *
     * @param fileOps The file operations tracker
     * @return Map with "readFiles" and "modifiedFiles" lists
     */
    public static Map<String, List<String>> computeFileLists(FileOperations fileOps) {
        Set<String> modified = new HashSet<>();
        modified.addAll(fileOps.getEdited());
        modified.addAll(fileOps.getWritten());

        List<String> readOnly = fileOps.getRead().stream()
                .filter(f -> !modified.contains(f))
                .sorted()
                .toList();

        List<String> modifiedFiles = modified.stream()
                .sorted()
                .toList();

        return Map.of(
                "readFiles", readOnly,
                "modifiedFiles", modifiedFiles
        );
    }

    /**
     * Format file operations as XML tags for summary.
     *
     * @param readFiles     Files that were only read
     * @param modifiedFiles Files that were modified
     * @return Formatted string, or empty if no files
     */
    public static String formatFileOperations(List<String> readFiles, List<String> modifiedFiles) {
        List<String> sections = new ArrayList<>();

        if (readFiles != null && !readFiles.isEmpty()) {
            sections.add("<read-files>\n" + String.join("\n", readFiles) + "\n</read-files>");
        }

        if (modifiedFiles != null && !modifiedFiles.isEmpty()) {
            sections.add("<modified-files>\n" + String.join("\n", modifiedFiles) + "\n</modified-files>");
        }

        if (sections.isEmpty()) {
            return "";
        }

        return "\n\n" + String.join("\n\n", sections);
    }
}
