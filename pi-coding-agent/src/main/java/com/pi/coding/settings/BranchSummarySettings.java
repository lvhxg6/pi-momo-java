package com.pi.coding.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Settings for branch summarization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchSummarySettings(
    @JsonProperty("reserveTokens") Integer reserveTokens,
    @JsonProperty("skipPrompt") Boolean skipPrompt
) {
    public static final BranchSummarySettings DEFAULT = new BranchSummarySettings(8000, false);

    public int getReserveTokens() { return reserveTokens != null ? reserveTokens : 8000; }
    public boolean isSkipPrompt() { return skipPrompt != null ? skipPrompt : false; }
}
