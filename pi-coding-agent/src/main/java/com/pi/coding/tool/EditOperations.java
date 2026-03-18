package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.util.concurrent.CompletableFuture;

/**
 * Operations interface for edit tool, allowing custom implementations
 * for remote or sandboxed file editing.
 */
public interface EditOperations {

    /**
     * Read file contents as bytes.
     *
     * @param absolutePath Absolute file path
     * @param signal Cancellation signal
     * @return File contents as byte array
     */
    CompletableFuture<byte[]> readFile(String absolutePath, CancellationSignal signal);

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
     * Check if file exists and is readable/writable.
     *
     * @param absolutePath Absolute file path
     * @param signal Cancellation signal
     * @return true if file is accessible
     */
    CompletableFuture<Boolean> access(String absolutePath, CancellationSignal signal);
}
