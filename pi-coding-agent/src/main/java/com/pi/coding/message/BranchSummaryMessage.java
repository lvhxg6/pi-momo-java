package com.pi.coding.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.agent.types.AgentMessage;

/**
 * Message type for branch summaries.
 *
 * <p><b>Validates: Requirement 23.5</b>
 *
 * @param summary   The branch summary text
 * @param fromId    ID of the entry where the branch started
 * @param timestamp Unix timestamp in milliseconds
 */
public record BranchSummaryMessage(
        @JsonProperty("summary") String summary,
        @JsonProperty("fromId") String fromId,
        @JsonProperty("timestamp") long timestamp
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
