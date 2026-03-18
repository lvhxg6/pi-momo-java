package com.pi.coding.compaction;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Settings for context compaction.
 *
 * <p><b>Validates: Requirement 3.3</b>
 *
 * @param enabled          Whether automatic compaction is enabled
 * @param reserveTokens    Tokens to reserve for response generation
 * @param keepRecentTokens Approximate tokens to keep from recent messages
 */
public record CompactionSettings(
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("reserveTokens") int reserveTokens,
        @JsonProperty("keepRecentTokens") int keepRecentTokens
) {

    /**
     * Default compaction settings.
     */
    public static final CompactionSettings DEFAULT = new CompactionSettings(true, 16384, 20000);

    /**
     * Create settings with compaction disabled.
     */
    public static CompactionSettings disabled() {
        return new CompactionSettings(false, DEFAULT.reserveTokens(), DEFAULT.keepRecentTokens());
    }
}
