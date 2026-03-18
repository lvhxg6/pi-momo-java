package com.pi.coding.compaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result from a compaction operation.
 *
 * <p><b>Validates: Requirement 3.8</b>
 *
 * @param <T>              Type of extension-specific details
 * @param summary          The generated summary of compacted messages
 * @param firstKeptEntryId ID of the first entry kept after compaction
 * @param tokensBefore     Token count before compaction
 * @param details          Extension-specific data (e.g., file lists)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompactionResult<T>(
        @JsonProperty("summary") String summary,
        @JsonProperty("firstKeptEntryId") String firstKeptEntryId,
        @JsonProperty("tokensBefore") int tokensBefore,
        @JsonProperty("details") T details
) {

    /**
     * Create a result without details.
     */
    public static CompactionResult<Void> of(String summary, String firstKeptEntryId, int tokensBefore) {
        return new CompactionResult<>(summary, firstKeptEntryId, tokensBefore, null);
    }

    /**
     * Create a result with file operation details.
     */
    public static CompactionResult<CompactionDetails> withFileOps(
            String summary,
            String firstKeptEntryId,
            int tokensBefore,
            List<String> readFiles,
            List<String> modifiedFiles
    ) {
        return new CompactionResult<>(
                summary,
                firstKeptEntryId,
                tokensBefore,
                new CompactionDetails(readFiles, modifiedFiles)
        );
    }
}
