package com.pi.coding.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Settings for context compaction.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompactionSettings(
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("reserveTokens") Integer reserveTokens,
    @JsonProperty("keepRecentTokens") Integer keepRecentTokens
) {
    public static final CompactionSettings DEFAULT = new CompactionSettings(true, 32000, 16000);

    public boolean isEnabled() { return enabled != null ? enabled : true; }
    public int getReserveTokens() { return reserveTokens != null ? reserveTokens : 32000; }
    public int getKeepRecentTokens() { return keepRecentTokens != null ? keepRecentTokens : 16000; }
}
