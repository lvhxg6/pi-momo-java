package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Operations interface for ls tool, allowing custom implementations
 * for remote or sandboxed directory listing.
 */
public interface LsOperations {

    /**
     * Check if path exists.
     *
     * @param absolutePath Absolute path to check
     * @param signal Cancellation signal
     * @return true if path exists
     */
    CompletableFuture<Boolean> exists(String absolutePath, CancellationSignal signal);

    /**
     * Check if path is a directory.
     *
     * @param absolutePath Absolute path to check
     * @param signal Cancellation signal
     * @return true if path is a directory
     */
    CompletableFuture<Boolean> isDirectory(String absolutePath, CancellationSignal signal);

    /**
     * Read directory entries.
     *
     * @param absolutePath Absolute directory path
     * @param signal Cancellation signal
     * @return List of directory entries with type info
     */
    CompletableFuture<List<DirEntry>> readdir(String absolutePath, CancellationSignal signal);

    /**
     * A directory entry.
     */
    record DirEntry(
        String name,
        boolean isDirectory
    ) {}
}
