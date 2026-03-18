package com.pi.coding.compaction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Details stored in CompactionEntry.details for file tracking.
 *
 * <p><b>Validates: Requirement 3.12</b>
 *
 * @param readFiles     Files that were only read (not modified)
 * @param modifiedFiles Files that were written or edited
 */
public record CompactionDetails(
        @JsonProperty("readFiles") List<String> readFiles,
        @JsonProperty("modifiedFiles") List<String> modifiedFiles
) {

    /**
     * Create empty details.
     */
    public static CompactionDetails empty() {
        return new CompactionDetails(List.of(), List.of());
    }
}
