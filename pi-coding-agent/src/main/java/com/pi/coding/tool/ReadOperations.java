package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.util.concurrent.CompletableFuture;

/**
 * Operations interface for read tool, allowing custom implementations
 * for remote or sandboxed file reading.
 */
public interface ReadOperations {

    /**
     * Read file content as text.
     *
     * @param path Absolute or relative file path
     * @param offset Line number to start reading from (1-indexed), or null for beginning
     * @param signal Cancellation signal
     * @return File content and metadata
     */
    CompletableFuture<ReadResult> readText(String path, Integer offset, CancellationSignal signal);

    /**
     * Read file content as base64 encoded binary (for images).
     *
     * @param path Absolute or relative file path
     * @param signal Cancellation signal
     * @return Base64 encoded content
     */
    CompletableFuture<String> readBase64(String path, CancellationSignal signal);

    /**
     * Get total line count of a file.
     *
     * @param path Absolute or relative file path
     * @param signal Cancellation signal
     * @return Total number of lines
     */
    CompletableFuture<Integer> getLineCount(String path, CancellationSignal signal);

    /**
     * Check if file exists.
     *
     * @param path Absolute or relative file path
     * @param signal Cancellation signal
     * @return true if file exists
     */
    CompletableFuture<Boolean> exists(String path, CancellationSignal signal);

    /**
     * Result of a text read operation.
     */
    record ReadResult(
        String content,
        int totalLines
    ) {}
}
