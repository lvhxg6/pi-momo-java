package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.util.concurrent.CompletableFuture;

/**
 * Operations interface for write tool, allowing custom implementations
 * for remote or sandboxed file writing.
 */
public interface WriteOperations {

    /**
     * Write content to a file.
     *
     * @param absolutePath Absolute file path
     * @param content Content to write
     * @param signal Cancellation signal
     * @return Completion future
     */
    CompletableFuture<Void> writeFile(String absolutePath, String content, CancellationSignal signal);

    /**
     * Create directory (recursively).
     *
     * @param dir Directory path
     * @param signal Cancellation signal
     * @return Completion future
     */
    CompletableFuture<Void> mkdir(String dir, CancellationSignal signal);
}
