package com.pi.coding.tool;

/**
 * Options for truncation operations.
 *
 * @param maxLines Maximum number of lines (default: 2000)
 * @param maxBytes Maximum number of bytes (default: 50KB)
 */
public record TruncationOptions(
    int maxLines,
    int maxBytes
) {
    /** Default maximum number of lines */
    public static final int DEFAULT_MAX_LINES = 2000;
    
    /** Default maximum number of bytes (50KB) */
    public static final int DEFAULT_MAX_BYTES = 50 * 1024;
    
    /**
     * Creates default truncation options.
     */
    public TruncationOptions() {
        this(DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES);
    }
    
    /**
     * Creates truncation options with custom max lines and default max bytes.
     *
     * @param maxLines Maximum number of lines
     */
    public TruncationOptions(int maxLines) {
        this(maxLines, DEFAULT_MAX_BYTES);
    }
}
