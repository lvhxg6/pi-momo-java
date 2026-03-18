package com.pi.coding.tool;

/**
 * Details returned by the find tool execution.
 *
 * @param pattern The glob pattern used
 * @param fileCount Number of files found
 * @param truncated Whether output was truncated
 * @param resultLimitReached The limit that was reached, or null
 */
public record FindToolDetails(
    String pattern,
    int fileCount,
    boolean truncated,
    Integer resultLimitReached
) {}
