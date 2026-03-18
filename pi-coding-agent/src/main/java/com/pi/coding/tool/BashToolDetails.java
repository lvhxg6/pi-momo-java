package com.pi.coding.tool;

/**
 * Details returned by the bash tool execution.
 *
 * @param command The command that was executed
 * @param exitCode Exit code of the command
 * @param tempFilePath Path to temp file with full output, if truncated
 * @param totalBytes Total bytes of output
 * @param outputBytes Bytes in the returned output
 * @param truncation Truncation result if truncation occurred
 */
public record BashToolDetails(
    String command,
    int exitCode,
    String tempFilePath,
    long totalBytes,
    long outputBytes,
    TruncationResult truncation
) {
    /**
     * Creates details for a non-truncated execution.
     */
    public static BashToolDetails simple(String command, int exitCode, long totalBytes) {
        return new BashToolDetails(command, exitCode, null, totalBytes, totalBytes, null);
    }

    /**
     * Creates details for a truncated execution.
     */
    public static BashToolDetails truncated(
            String command,
            int exitCode,
            String tempFilePath,
            TruncationResult truncation) {
        return new BashToolDetails(
            command, exitCode, tempFilePath,
            truncation.totalBytes(), truncation.outputBytes(), truncation);
    }
}
