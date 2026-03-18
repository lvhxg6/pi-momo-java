package com.pi.coding.tool;

/**
 * Details returned by the grep tool execution.
 *
 * @param pattern The search pattern used
 * @param matchCount Number of matches found
 * @param truncated Whether output was truncated
 * @param matchLimitReached The limit that was reached, or null
 * @param linesTruncated Whether any lines were truncated
 */
public record GrepToolDetails(
    String pattern,
    int matchCount,
    boolean truncated,
    Integer matchLimitReached,
    boolean linesTruncated
) {}
