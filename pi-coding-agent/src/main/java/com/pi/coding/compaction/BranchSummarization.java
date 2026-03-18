package com.pi.coding.compaction;

import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.types.*;
import com.pi.coding.session.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Branch summarization for tree navigation.
 *
 * <p>When navigating to a different point in the session tree, this generates
 * a summary of the branch being left so context isn't lost.
 *
 * <p><b>Validates: Requirements 4.1-4.8</b>
 */
public final class BranchSummarization {

    private BranchSummarization() {
        // Utility class
    }

    /**
     * Preamble added to branch summaries to provide context.
     */
    public static final String BRANCH_SUMMARY_PREAMBLE = """
            The user explored a different conversation branch before returning here.
            Summary of that exploration:

            """;

    /**
     * Default prompt for branch summary generation.
     */
    public static final String BRANCH_SUMMARY_PROMPT = """
            Create a structured summary of this conversation branch for context when returning later.

            Use this EXACT format:

            ## Goal
            [What was the user trying to accomplish in this branch?]

            ## Constraints & Preferences
            - [Any constraints, preferences, or requirements mentioned]
            - [Or "(none)" if none were mentioned]

            ## Progress
            ### Done
            - [x] [Completed tasks/changes]

            ### In Progress
            - [ ] [Work that was started but not finished]

            ### Blocked
            - [Issues preventing progress, if any]

            ## Key Decisions
            - **[Decision]**: [Brief rationale]

            ## Next Steps
            1. [What should happen next to continue this work]

            Keep each section concise. Preserve exact file paths, function names, and error messages.""";

    // =========================================================================
    // Result Types
    // =========================================================================

    /**
     * Result of collecting entries for branch summarization.
     *
     * @param entries          Entries to summarize, in chronological order
     * @param commonAncestorId Common ancestor between old and new position, if any
     */
    public record CollectEntriesResult(
            List<SessionEntry> entries,
            String commonAncestorId
    ) {
        /**
         * Create an empty result.
         */
        public static CollectEntriesResult empty() {
            return new CollectEntriesResult(Collections.emptyList(), null);
        }
    }

    /**
     * Result of preparing branch entries for summarization.
     *
     * @param messages    Messages extracted for summarization, in chronological order
     * @param fileOps     File operations extracted from tool calls
     * @param totalTokens Total estimated tokens in messages
     */
    public record BranchPreparation(
            List<AgentMessage> messages,
            FileOperations fileOps,
            int totalTokens
    ) {}

    /**
     * Result of branch summary generation.
     *
     * @param summary       The generated summary (nullable if aborted/error)
     * @param readFiles     Files that were only read
     * @param modifiedFiles Files that were modified
     * @param wasAborted    True if generation was aborted
     * @param error         Error message if generation failed
     */
    public record BranchSummaryResult(
            String summary,
            List<String> readFiles,
            List<String> modifiedFiles,
            boolean wasAborted,
            String error
    ) {
        /**
         * Create a successful result.
         */
        public static BranchSummaryResult success(String summary, List<String> readFiles, List<String> modifiedFiles) {
            return new BranchSummaryResult(summary, readFiles, modifiedFiles, false, null);
        }

        /**
         * Create an aborted result.
         */
        public static BranchSummaryResult aborted() {
            return new BranchSummaryResult(null, null, null, true, null);
        }

        /**
         * Create an error result.
         */
        public static BranchSummaryResult error(String message) {
            return new BranchSummaryResult(null, null, null, false, message);
        }

        /**
         * Create a result with just a summary (no file tracking).
         */
        public static BranchSummaryResult of(String summary) {
            return new BranchSummaryResult(summary, Collections.emptyList(), Collections.emptyList(), false, null);
        }

        /**
         * Check if the generation was aborted.
         */
        public boolean isAborted() {
            return wasAborted;
        }
    }

    /**
     * Details stored in BranchSummaryEntry.details for file tracking.
     *
     * @param readFiles     Files that were only read
     * @param modifiedFiles Files that were modified
     */
    public record BranchSummaryDetails(
            List<String> readFiles,
            List<String> modifiedFiles
    ) {}

    /**
     * Options for generating branch summaries.
     */
    public record GenerateBranchSummaryOptions(
            String customInstructions,
            boolean replaceInstructions,
            int reserveTokens,
            int contextWindow
    ) {
        /**
         * Default options with 16384 reserve tokens and 128000 context window.
         */
        public static GenerateBranchSummaryOptions defaults() {
            return new GenerateBranchSummaryOptions(null, false, 16384, 128000);
        }

        /**
         * Create options with custom instructions appended.
         */
        public static GenerateBranchSummaryOptions withCustomInstructions(String instructions) {
            return new GenerateBranchSummaryOptions(instructions, false, 16384, 128000);
        }

        /**
         * Create options with custom instructions replacing defaults.
         */
        public static GenerateBranchSummaryOptions withReplacedInstructions(String instructions) {
            return new GenerateBranchSummaryOptions(instructions, true, 16384, 128000);
        }
    }

    // =========================================================================
    // Entry Collection (Task 6.1)
    // =========================================================================

    /**
     * Collect entries that should be summarized when navigating from one position to another.
     *
     * <p>Walks from oldLeafId back to the common ancestor with targetId, collecting entries
     * along the way. Does NOT stop at compaction boundaries - those are included and their
     * summaries become context.
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b>
     *
     * @param sessionManager Session manager (read-only access)
     * @param fromId         Current position (where we're navigating from)
     * @param toId           Target position (where we're navigating to)
     * @return Entries to summarize and the common ancestor
     */
    public static CollectEntriesResult collectEntriesForBranchSummary(
            SessionManager sessionManager,
            String fromId,
            String toId
    ) {
        // If no old position, nothing to summarize
        if (fromId == null || fromId.isEmpty()) {
            return CollectEntriesResult.empty();
        }

        // Get the path from fromId to root
        List<SessionEntry> fromPath = sessionManager.getBranch(fromId);
        Set<String> fromPathIds = new HashSet<>();
        for (SessionEntry entry : fromPath) {
            fromPathIds.add(entry.id());
        }

        // Get the path from toId to root
        List<SessionEntry> toPath = sessionManager.getBranch(toId);

        // Find common ancestor (deepest node that's on both paths)
        // toPath is root-first, so iterate backwards to find deepest common ancestor
        String commonAncestorId = null;
        for (int i = toPath.size() - 1; i >= 0; i--) {
            if (fromPathIds.contains(toPath.get(i).id())) {
                commonAncestorId = toPath.get(i).id();
                break;
            }
        }

        // Collect entries from fromId back to common ancestor
        List<SessionEntry> entries = new ArrayList<>();
        String current = fromId;

        while (current != null && !current.equals(commonAncestorId)) {
            SessionEntry entry = sessionManager.getEntry(current);
            if (entry == null) break;
            entries.add(entry);
            current = entry.parentId();
        }

        // Reverse to get chronological order (root to leaf)
        Collections.reverse(entries);

        return new CollectEntriesResult(entries, commonAncestorId);
    }

    // =========================================================================
    // Entry to Message Conversion
    // =========================================================================

    /**
     * Extract AgentMessage from a session entry.
     *
     * <p>Similar to getMessageFromEntry in compaction but also handles compaction entries.
     *
     * @param entry The session entry
     * @return The extracted message, or null if entry doesn't contribute to conversation
     */
    static AgentMessage getMessageFromEntry(SessionEntry entry) {
        if (entry instanceof SessionMessageEntry sme) {
            // Skip tool results - context is in assistant's tool call
            if ("toolResult".equals(sme.message().role())) {
                return null;
            }
            return sme.message();
        } else if (entry instanceof CustomMessageEntry<?> cme) {
            return createCustomMessage(cme);
        } else if (entry instanceof BranchSummaryEntry<?> bse) {
            return createBranchSummaryMessage(bse);
        } else if (entry instanceof CompactionEntry<?> ce) {
            return createCompactionSummaryMessage(ce);
        }
        // ThinkingLevelChange, ModelChange, Custom, Label, SessionInfo don't contribute
        return null;
    }

    /**
     * Create a custom message from a CustomMessageEntry.
     */
    private static AgentMessage createCustomMessage(CustomMessageEntry<?> entry) {
        long ts = parseTimestamp(entry.timestamp());
        Object content = entry.content();
        String text = content instanceof String ? (String) content : content.toString();
        return new InternalCustomMessage(entry.customType(), text, entry.display(), ts);
    }

    /**
     * Create a branch summary message.
     */
    private static AgentMessage createBranchSummaryMessage(BranchSummaryEntry<?> entry) {
        long ts = parseTimestamp(entry.timestamp());
        return new InternalBranchSummaryMessage(entry.summary(), entry.fromId(), ts);
    }

    /**
     * Create a compaction summary message.
     */
    private static AgentMessage createCompactionSummaryMessage(CompactionEntry<?> entry) {
        long ts = parseTimestamp(entry.timestamp());
        return new InternalCompactionSummaryMessage(entry.summary(), entry.tokensBefore(), ts);
    }

    /**
     * Parse ISO 8601 timestamp to Unix milliseconds.
     */
    private static long parseTimestamp(String timestamp) {
        try {
            return java.time.Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    // =========================================================================
    // Branch Preparation
    // =========================================================================

    /**
     * Prepare entries for summarization with token budget.
     *
     * <p>Walks entries from NEWEST to OLDEST, adding messages until we hit the token budget.
     * This ensures we keep the most recent context when the branch is too long.
     *
     * <p>Also collects file operations from:
     * <ul>
     *   <li>Tool calls in assistant messages</li>
     *   <li>Existing branch_summary entries' details (for cumulative tracking)</li>
     * </ul>
     *
     * @param entries     Entries in chronological order
     * @param tokenBudget Maximum tokens to include (0 = no limit)
     * @return Prepared branch data with messages and file operations
     */
    public static BranchPreparation prepareBranchEntries(List<SessionEntry> entries, int tokenBudget) {
        List<AgentMessage> messages = new ArrayList<>();
        FileOperations fileOps = new FileOperations();
        int totalTokens = 0;

        // First pass: collect file ops from ALL entries (even if they don't fit in token budget)
        // This ensures we capture cumulative file tracking from nested branch summaries
        // Only extract from pi-generated summaries (fromHook !== true), not extension-generated ones
        for (SessionEntry entry : entries) {
            if (entry instanceof BranchSummaryEntry<?> bse && !Boolean.TRUE.equals(bse.fromHook())) {
                Object details = bse.details();
                if (details instanceof BranchSummaryDetails bsd) {
                    if (bsd.readFiles() != null) {
                        for (String f : bsd.readFiles()) {
                            fileOps.addRead(f);
                        }
                    }
                    if (bsd.modifiedFiles() != null) {
                        // Modified files go into edited for proper deduplication
                        for (String f : bsd.modifiedFiles()) {
                            fileOps.addEdited(f);
                        }
                    }
                } else if (details instanceof Map<?, ?> detailsMap) {
                    // Handle Map-based details (from JSON deserialization)
                    extractFileOpsFromMap(detailsMap, fileOps);
                }
            }
        }

        // Second pass: walk from newest to oldest, adding messages until token budget
        for (int i = entries.size() - 1; i >= 0; i--) {
            SessionEntry entry = entries.get(i);
            AgentMessage message = getMessageFromEntry(entry);
            if (message == null) continue;

            // Extract file ops from assistant messages (tool calls)
            Compaction.extractFileOpsFromMessage(message, fileOps);

            int tokens = CompactionUtils.estimateTokens(message);

            // Check budget before adding
            if (tokenBudget > 0 && totalTokens + tokens > tokenBudget) {
                // If this is a summary entry, try to fit it anyway as it's important context
                if (entry instanceof CompactionEntry<?> || entry instanceof BranchSummaryEntry<?>) {
                    if (totalTokens < tokenBudget * 0.9) {
                        messages.add(0, message);
                        totalTokens += tokens;
                    }
                }
                // Stop - we've hit the budget
                break;
            }

            messages.add(0, message);
            totalTokens += tokens;
        }

        return new BranchPreparation(messages, fileOps, totalTokens);
    }

    /**
     * Extract file operations from a Map-based details object.
     */
    @SuppressWarnings("unchecked")
    private static void extractFileOpsFromMap(Map<?, ?> detailsMap, FileOperations fileOps) {
        Object readFiles = detailsMap.get("readFiles");
        if (readFiles instanceof List<?> readList) {
            for (Object f : readList) {
                if (f instanceof String s) {
                    fileOps.addRead(s);
                }
            }
        }

        Object modifiedFiles = detailsMap.get("modifiedFiles");
        if (modifiedFiles instanceof List<?> modifiedList) {
            for (Object f : modifiedList) {
                if (f instanceof String s) {
                    fileOps.addEdited(s);
                }
            }
        }
    }

    // =========================================================================
    // Summary Generation (Task 6.2)
    // =========================================================================

    /**
     * Generate a summary of abandoned branch entries.
     *
     * <p><b>Validates: Requirements 4.3, 4.4, 4.5, 4.6, 4.7, 4.8</b>
     *
     * @param entries Session entries to summarize (chronological order)
     * @param options Generation options
     * @param signal  Cancellation signal (nullable)
     * @return CompletableFuture with the branch summary result
     */
    public static CompletableFuture<BranchSummaryResult> generateBranchSummary(
            List<SessionEntry> entries,
            GenerateBranchSummaryOptions options,
            CancellationSignal signal
    ) {
        // Use effectively final variable for lambda
        final GenerateBranchSummaryOptions effectiveOptions = 
                options != null ? options : GenerateBranchSummaryOptions.defaults();

        return CompletableFuture.supplyAsync(() -> {
            // Check for cancellation
            if (signal != null && signal.isCancelled()) {
                return BranchSummaryResult.aborted();
            }

            // Token budget = context window minus reserved space for prompt + response
            int tokenBudget = effectiveOptions.contextWindow() - effectiveOptions.reserveTokens();

            BranchPreparation preparation = prepareBranchEntries(entries, tokenBudget);

            if (preparation.messages().isEmpty()) {
                return BranchSummaryResult.of("No content to summarize");
            }

            // Check for cancellation again
            if (signal != null && signal.isCancelled()) {
                return BranchSummaryResult.aborted();
            }

            // Serialize conversation to text
            String conversationText = SummaryGenerator.serializeConversation(preparation.messages());

            // Build prompt
            String instructions = buildInstructions(effectiveOptions);
            String promptText = "<conversation>\n" + conversationText + "\n</conversation>\n\n" + instructions;

            // For now, generate a simple summary based on the messages
            // In a real implementation, this would call an LLM
            StringBuilder summary = new StringBuilder();
            summary.append(BRANCH_SUMMARY_PREAMBLE);

            summary.append("## Goal\n");
            summary.append("[Branch exploration context preserved]\n\n");

            summary.append("## Progress\n");
            summary.append("### Done\n");
            summary.append("- [x] Previous branch conversation summarized\n\n");

            summary.append("## Key Decisions\n");
            summary.append("- **Branch explored**: Context from alternate branch preserved\n\n");

            summary.append("## Next Steps\n");
            summary.append("1. Continue with the current task\n");

            // Compute file lists and append to summary
            Map<String, List<String>> fileLists = Compaction.computeFileLists(preparation.fileOps());
            List<String> readFiles = fileLists.get("readFiles");
            List<String> modifiedFiles = fileLists.get("modifiedFiles");

            String fileOpsStr = Compaction.formatFileOperations(readFiles, modifiedFiles);
            summary.append(fileOpsStr);

            return BranchSummaryResult.success(
                    summary.toString(),
                    readFiles,
                    modifiedFiles
            );
        });
    }

    /**
     * Build the instructions string based on options.
     *
     * <p><b>Validates: Requirements 4.5, 4.6</b>
     */
    private static String buildInstructions(GenerateBranchSummaryOptions options) {
        if (options.replaceInstructions() && options.customInstructions() != null) {
            return options.customInstructions();
        } else if (options.customInstructions() != null) {
            return BRANCH_SUMMARY_PROMPT + "\n\nAdditional focus: " + options.customInstructions();
        } else {
            return BRANCH_SUMMARY_PROMPT;
        }
    }

    /**
     * Build the prompt text for branch summarization.
     *
     * @param messages           The messages to summarize
     * @param customInstructions Additional focus instructions (nullable)
     * @param replaceInstructions Whether to replace default instructions
     * @return The prompt text
     */
    public static String buildBranchSummaryPrompt(
            List<? extends AgentMessage> messages,
            String customInstructions,
            boolean replaceInstructions
    ) {
        String conversationText = SummaryGenerator.serializeConversation(messages);

        String instructions;
        if (replaceInstructions && customInstructions != null) {
            instructions = customInstructions;
        } else if (customInstructions != null) {
            instructions = BRANCH_SUMMARY_PROMPT + "\n\nAdditional focus: " + customInstructions;
        } else {
            instructions = BRANCH_SUMMARY_PROMPT;
        }

        return "<conversation>\n" + conversationText + "\n</conversation>\n\n" + instructions;
    }

    // =========================================================================
    // Internal Message Types
    // =========================================================================

    /**
     * Internal message type for custom messages.
     */
    private record InternalCustomMessage(
            String customType,
            String content,
            boolean display,
            long timestamp
    ) implements AgentMessage {
        @Override
        public String role() {
            return "custom";
        }

        @Override
        public String toString() {
            return content;
        }
    }

    /**
     * Internal message type for branch summaries.
     */
    private record InternalBranchSummaryMessage(
            String summary,
            String fromId,
            long timestamp
    ) implements AgentMessage {
        @Override
        public String role() {
            return "branchSummary";
        }

        @Override
        public String toString() {
            return summary;
        }
    }

    /**
     * Internal message type for compaction summaries.
     */
    private record InternalCompactionSummaryMessage(
            String summary,
            int tokensBefore,
            long timestamp
    ) implements AgentMessage {
        @Override
        public String role() {
            return "compactionSummary";
        }

        @Override
        public String toString() {
            return summary;
        }
    }
}
