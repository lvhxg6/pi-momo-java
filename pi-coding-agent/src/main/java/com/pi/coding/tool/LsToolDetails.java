package com.pi.coding.tool;

/**
 * Details returned by the ls tool execution.
 *
 * @param path The directory path listed
 * @param entryCount Number of entries found
 * @param truncated Whether output was truncated
 * @param entryLimitReached The limit that was reached, or null
 */
public record LsToolDetails(
    String path,
    int entryCount,
    boolean truncated,
    Integer entryLimitReached
) {}
