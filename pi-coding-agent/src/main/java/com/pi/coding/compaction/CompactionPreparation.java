package com.pi.coding.compaction;

import com.pi.agent.types.AgentMessage;

import java.util.List;

/**
 * Prepared data for compaction (for extensions to customize).
 *
 * <p><b>Validates: Requirement 3.8</b>
 *
 * @param firstKeptEntryId     UUID of first entry to keep
 * @param messagesToSummarize  Messages that will be summarized and discarded
 * @param turnPrefixMessages   Messages that will be turned into turn prefix summary (if splitting)
 * @param isSplitTurn          Whether this is a split turn (cut point in middle of turn)
 * @param tokensBefore         Token count before compaction
 * @param previousSummary      Summary from previous compaction, for iterative update
 * @param fileOps              File operations extracted from messagesToSummarize
 * @param settings             Compaction settings
 */
public record CompactionPreparation(
        String firstKeptEntryId,
        List<AgentMessage> messagesToSummarize,
        List<AgentMessage> turnPrefixMessages,
        boolean isSplitTurn,
        int tokensBefore,
        String previousSummary,
        FileOperations fileOps,
        CompactionSettings settings
) {

    /**
     * Check if this preparation has messages to summarize.
     */
    public boolean hasMessagesToSummarize() {
        return messagesToSummarize != null && !messagesToSummarize.isEmpty();
    }

    /**
     * Check if this preparation has turn prefix messages.
     */
    public boolean hasTurnPrefixMessages() {
        return turnPrefixMessages != null && !turnPrefixMessages.isEmpty();
    }
}
