package com.pi.coding.tool;

/**
 * Details returned by the read tool execution.
 *
 * @param path The file path that was read
 * @param totalLines Total number of lines in the file
 * @param outputLines Number of lines in the output
 * @param nextOffset Next offset to use for continuation, or null if no more content
 * @param mimeType MIME type for image files, or null for text files
 * @param truncation Truncation result if truncation occurred
 */
public record ReadToolDetails(
    String path,
    int totalLines,
    int outputLines,
    Integer nextOffset,
    String mimeType,
    TruncationResult truncation
) {
    /**
     * Creates details for a text file read.
     */
    public static ReadToolDetails forText(
            String path,
            int totalLines,
            int outputLines,
            Integer nextOffset,
            TruncationResult truncation) {
        return new ReadToolDetails(path, totalLines, outputLines, nextOffset, null, truncation);
    }

    /**
     * Creates details for an image file read.
     */
    public static ReadToolDetails forImage(String path, String mimeType) {
        return new ReadToolDetails(path, 0, 0, null, mimeType, null);
    }
}
