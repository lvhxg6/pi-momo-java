package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.util.concurrent.CompletableFuture;

/**
 * Operations interface for grep tool, allowing custom implementations
 * for remote or sandboxed file searching.
 */
public interface GrepOperations {

    /**
     * Check if path is a directory.
     *
     * @param absolutePath Absolute path to check
     * @param signal Cancellation signal
     * @return true if path is a directory
     */
    CompletableFuture<Boolean> isDirectory(String absolutePath, CancellationSignal signal);

    /**
     * Read file contents.
     *
     * @param absolutePath Absolute file path
     * @param signal Cancellation signal
     * @return File contents
     */
    CompletableFuture<String> readFile(String absolutePath, CancellationSignal signal);

    /**
     * Search for pattern in files.
     *
     * @param pattern Search pattern (regex or literal)
     * @param searchPath Path to search in
     * @param options Search options
     * @param signal Cancellation signal
     * @return Search results
     */
    CompletableFuture<GrepResult> grep(String pattern, String searchPath, GrepOptions options, CancellationSignal signal);

    /**
     * Options for grep search.
     */
    record GrepOptions(
        String glob,
        boolean ignoreCase,
        boolean literal,
        int context,
        int limit
    ) {}

    /**
     * Result of grep search.
     */
    record GrepResult(
        java.util.List<GrepMatch> matches,
        boolean limitReached
    ) {}

    /**
     * A single grep match.
     */
    record GrepMatch(
        String filePath,
        int lineNumber,
        String lineContent
    ) {}
}
