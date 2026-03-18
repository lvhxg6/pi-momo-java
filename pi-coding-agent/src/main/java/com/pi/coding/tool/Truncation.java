package com.pi.coding.tool;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared truncation utilities for tool outputs.
 * <p>
 * Truncation is based on two independent limits - whichever is hit first wins:
 * <ul>
 *   <li>Line limit (default: 2000 lines)</li>
 *   <li>Byte limit (default: 50KB)</li>
 * </ul>
 * <p>
 * Never returns partial lines (except bash tail truncation edge case).
 */
public final class Truncation {
    
    private Truncation() {
        // Utility class
    }
    
    /**
     * Format bytes as human-readable size.
     *
     * @param bytes Number of bytes
     * @return Human-readable size string (e.g., "1.5KB", "2.3MB")
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Truncate content from the head (keep first N lines/bytes).
     * Suitable for file reads where you want to see the beginning.
     * <p>
     * Never returns partial lines. If first line exceeds byte limit,
     * returns empty content with firstLineExceedsLimit=true.
     *
     * @param content The content to truncate
     * @return Truncation result
     */
    public static TruncationResult truncateHead(String content) {
        return truncateHead(content, new TruncationOptions());
    }
    
    /**
     * Truncate content from the head (keep first N lines/bytes).
     * Suitable for file reads where you want to see the beginning.
     * <p>
     * Never returns partial lines. If first line exceeds byte limit,
     * returns empty content with firstLineExceedsLimit=true.
     *
     * @param content The content to truncate
     * @param options Truncation options
     * @return Truncation result
     */
    public static TruncationResult truncateHead(String content, TruncationOptions options) {
        int maxLines = options.maxLines();
        int maxBytes = options.maxBytes();
        
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        long totalBytes = contentBytes.length;
        String[] lines = content.split("\n", -1);
        int totalLines = lines.length;
        
        // Check if no truncation needed
        if (totalLines <= maxLines && totalBytes <= maxBytes) {
            return TruncationResult.noTruncation(content, totalLines, totalBytes);
        }
        
        // Check if first line alone exceeds byte limit
        long firstLineBytes = lines[0].getBytes(StandardCharsets.UTF_8).length;
        if (firstLineBytes > maxBytes) {
            return TruncationResult.truncatedByBytes(
                "",
                totalLines,
                totalBytes,
                0,
                0,
                false,
                true
            );
        }
        
        // Collect complete lines that fit
        List<String> outputLinesArr = new ArrayList<>();
        long outputBytesCount = 0;
        String truncatedBy = "lines";
        
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            String line = lines[i];
            long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + (i > 0 ? 1 : 0); // +1 for newline
            
            if (outputBytesCount + lineBytes > maxBytes) {
                truncatedBy = "bytes";
                break;
            }
            
            outputLinesArr.add(line);
            outputBytesCount += lineBytes;
        }
        
        // If we exited due to line limit
        if (outputLinesArr.size() >= maxLines && outputBytesCount <= maxBytes) {
            truncatedBy = "lines";
        }
        
        String outputContent = String.join("\n", outputLinesArr);
        long finalOutputBytes = outputContent.getBytes(StandardCharsets.UTF_8).length;
        
        if ("lines".equals(truncatedBy)) {
            return TruncationResult.truncatedByLines(
                outputContent,
                totalLines,
                totalBytes,
                outputLinesArr.size(),
                finalOutputBytes
            );
        } else {
            return TruncationResult.truncatedByBytes(
                outputContent,
                totalLines,
                totalBytes,
                outputLinesArr.size(),
                finalOutputBytes,
                false,
                false
            );
        }
    }
    
    /**
     * Truncate content from the tail (keep last N lines/bytes).
     * Suitable for bash output where you want to see the end (errors, final results).
     * <p>
     * May return partial first line if the last line of original content exceeds byte limit.
     *
     * @param content The content to truncate
     * @return Truncation result
     */
    public static TruncationResult truncateTail(String content) {
        return truncateTail(content, new TruncationOptions());
    }
    
    /**
     * Truncate content from the tail (keep last N lines/bytes).
     * Suitable for bash output where you want to see the end (errors, final results).
     * <p>
     * May return partial first line if the last line of original content exceeds byte limit.
     *
     * @param content The content to truncate
     * @param options Truncation options
     * @return Truncation result
     */
    public static TruncationResult truncateTail(String content, TruncationOptions options) {
        int maxLines = options.maxLines();
        int maxBytes = options.maxBytes();
        
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        long totalBytes = contentBytes.length;
        String[] lines = content.split("\n", -1);
        int totalLines = lines.length;
        
        // Check if no truncation needed
        if (totalLines <= maxLines && totalBytes <= maxBytes) {
            return TruncationResult.noTruncation(content, totalLines, totalBytes);
        }
        
        // Work backwards from the end
        List<String> outputLinesArr = new ArrayList<>();
        long outputBytesCount = 0;
        String truncatedBy = "lines";
        boolean lastLinePartial = false;
        
        for (int i = lines.length - 1; i >= 0 && outputLinesArr.size() < maxLines; i--) {
            String line = lines[i];
            long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + (outputLinesArr.isEmpty() ? 0 : 1); // +1 for newline
            
            if (outputBytesCount + lineBytes > maxBytes) {
                truncatedBy = "bytes";
                // Edge case: if we haven't added ANY lines yet and this line exceeds maxBytes,
                // take the end of the line (partial)
                if (outputLinesArr.isEmpty()) {
                    String truncatedLine = truncateStringToBytesFromEnd(line, maxBytes);
                    outputLinesArr.add(0, truncatedLine);
                    outputBytesCount = truncatedLine.getBytes(StandardCharsets.UTF_8).length;
                    lastLinePartial = true;
                }
                break;
            }
            
            outputLinesArr.add(0, line);
            outputBytesCount += lineBytes;
        }
        
        // If we exited due to line limit
        if (outputLinesArr.size() >= maxLines && outputBytesCount <= maxBytes) {
            truncatedBy = "lines";
        }
        
        String outputContent = String.join("\n", outputLinesArr);
        long finalOutputBytes = outputContent.getBytes(StandardCharsets.UTF_8).length;
        
        if ("lines".equals(truncatedBy)) {
            return TruncationResult.truncatedByLines(
                outputContent,
                totalLines,
                totalBytes,
                outputLinesArr.size(),
                finalOutputBytes
            );
        } else {
            return TruncationResult.truncatedByBytes(
                outputContent,
                totalLines,
                totalBytes,
                outputLinesArr.size(),
                finalOutputBytes,
                lastLinePartial,
                false
            );
        }
    }
    
    /**
     * Truncate a single line to a maximum length.
     *
     * @param line The line to truncate
     * @param maxLength Maximum length in characters
     * @return Truncated line with ellipsis if truncated
     */
    public static String truncateLine(String line, int maxLength) {
        if (line.length() <= maxLength) {
            return line;
        }
        if (maxLength <= 3) {
            return line.substring(0, maxLength);
        }
        return line.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Truncate a string to fit within a byte limit (from the end).
     * Handles multi-byte UTF-8 characters correctly.
     *
     * @param str The string to truncate
     * @param maxBytes Maximum number of bytes
     * @return Truncated string keeping the end
     */
    private static String truncateStringToBytesFromEnd(String str, int maxBytes) {
        byte[] buf = str.getBytes(StandardCharsets.UTF_8);
        if (buf.length <= maxBytes) {
            return str;
        }
        
        // Start from the end, skip maxBytes back
        int start = buf.length - maxBytes;
        
        // Find a valid UTF-8 boundary (start of a character)
        // UTF-8 continuation bytes have the pattern 10xxxxxx (0x80-0xBF)
        while (start < buf.length && (buf[start] & 0xC0) == 0x80) {
            start++;
        }
        
        return new String(buf, start, buf.length - start, StandardCharsets.UTF_8);
    }
}
