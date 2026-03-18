package com.pi.coding.session;

import com.pi.coding.compaction.CompactionResult;

/**
 * Event emitted when auto-compaction ends.
 *
 * @param result       The compaction result (null if failed/aborted)
 * @param aborted      Whether compaction was aborted
 * @param willRetry    Whether the agent will retry after compaction
 * @param errorMessage Error message if compaction failed (nullable)
 */
public record AutoCompactionEndEvent(
        CompactionResult result,
        boolean aborted,
        boolean willRetry,
        String errorMessage
) implements AgentSessionEvent {
}
