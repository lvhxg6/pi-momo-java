package com.pi.coding.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.agent.types.AgentMessage;

/**
 * Message type for compaction summaries.
 *
 * <p><b>Validates: Requirement 23.5</b>
 *
 * @param summary      The compaction summary text
 * @param tokensBefore Token count before compaction
 * @param timestamp    Unix timestamp in milliseconds
 */
public record CompactionSummaryMessage(
        @JsonProperty("summary") String summary,
        @JsonProperty("tokensBefore") int tokensBefore,
        @JsonProperty("timestamp") long timestamp
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
