package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of EditOperations using local file system.
 */
public class DefaultEditOperations implements EditOperations {

    private final String cwd;

    /**
     * Creates a new DefaultEditOperations with the specified working directory.
     *
     * @param cwd Current working directory for resolving relative paths
     */
    public DefaultEditOperations(String cwd) {
        this.cwd = cwd;
    }

    @Override
    public CompletableFuture<byte[]> readFile(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readAllBytes(Path.of(absolutePath));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + absolutePath + " - " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> writeFile(String absolutePath, String content, CancellationSignal signal) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(Path.of(absolutePath), content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write file: " + absolutePath + " - " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> access(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            Path path = Path.of(absolutePath);
            return Files.exists(path) && Files.isReadable(path) && Files.isWritable(path);
        });
    }

    /**
     * Resolve a path relative to the working directory.
     *
     * @param path Path to resolve
     * @return Absolute path
     */
    public String resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.toString();
        }
        return Paths.get(cwd).resolve(path).toString();
    }
}
