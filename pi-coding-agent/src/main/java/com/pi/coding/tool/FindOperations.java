package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Operations interface for find tool, allowing custom implementations
 * for remote or sandboxed file searching.
 */
public interface FindOperations {

    /**
     * Check if path exists.
     *
     * @param absolutePath Absolute path to check
     * @param signal Cancellation signal
     * @return true if path exists
     */
    CompletableFuture<Boolean> exists(String absolutePath, CancellationSignal signal);

    /**
     * Find files matching glob pattern.
     *
     * @param pattern Glob pattern
     * @param searchPath Path to search in
     * @param ignore Patterns to ignore
     * @param limit Maximum number of results
     * @param signal Cancellation signal
     * @return List of matching file paths (relative to searchPath)
     */
    CompletableFuture<List<String>> glob(String pattern, String searchPath, List<String> ignore, int limit, CancellationSignal signal);
}
