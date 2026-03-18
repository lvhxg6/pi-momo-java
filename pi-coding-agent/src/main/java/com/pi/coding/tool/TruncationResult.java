package com.pi.coding.tool;

/**
 * Result of a truncation operation.
 *
 * @param content The truncated content
 * @param truncated Whether truncation occurred
 * @param truncatedBy Which limit was hit: "lines", "bytes", or null if not truncated
 * @param totalLines Total number of lines in the original content
 * @param totalBytes Total number of bytes in the original content
 * @param outputLines Number of complete lines in the truncated output
 * @param outputBytes Number of bytes in the truncated output
 * @param lastLinePartial Whether the last line was partially truncated (only for tail truncation edge case)
 * @param firstLineExceedsLimit Whether the first line exceeded the byte limit (for head truncation)
 */
public record TruncationResult(
    String content,
    boolean truncated,
    String truncatedBy,  // "lines", "bytes", or null
    int totalLines,
    long totalBytes,
    int outputLines,
    long outputBytes,
    boolean lastLinePartial,
    boolean firstLineExceedsLimit
) {
    /**
     * Creates a result indicating no truncation was needed.
     */
    public static TruncationResult noTruncation(String content, int totalLines, long totalBytes) {
        return new TruncationResult(
            content,
            false,
            null,
            totalLines,
            totalBytes,
            totalLines,
            totalBytes,
            false,
            false
        );
    }
    
    /**
     * Creates a result indicating truncation by lines.
     */
    public static TruncationResult truncatedByLines(
            String content,
            int totalLines,
            long totalBytes,
            int outputLines,
            long outputBytes) {
        return new TruncationResult(
            content,
            true,
            "lines",
            totalLines,
            totalBytes,
            outputLines,
            outputBytes,
            false,
            false
        );
    }
    
    /**
     * Creates a result indicating truncation by bytes.
     */
    public static TruncationResult truncatedByBytes(
            String content,
            int totalLines,
            long totalBytes,
            int outputLines,
            long outputBytes,
            boolean lastLinePartial,
            boolean firstLineExceedsLimit) {
        return new TruncationResult(
            content,
            true,
            "bytes",
            totalLines,
            totalBytes,
            outputLines,
            outputBytes,
            lastLinePartial,
            firstLineExceedsLimit
        );
    }
}
