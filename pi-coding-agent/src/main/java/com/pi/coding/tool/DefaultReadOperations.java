package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of ReadOperations using local file system.
 */
public class DefaultReadOperations implements ReadOperations {

    private final String cwd;

    /**
     * Creates a new DefaultReadOperations with the specified working directory.
     *
     * @param cwd Current working directory for resolving relative paths
     */
    public DefaultReadOperations(String cwd) {
        this.cwd = cwd;
    }

    @Override
    public CompletableFuture<ReadResult> readText(String path, Integer offset, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = resolvePath(path);
                
                // Count total lines
                int totalLines;
                try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                    totalLines = (int) lines.count();
                }
                
                // Read content with offset
                String content;
                int startLine = offset != null ? Math.max(1, offset) : 1;
                
                try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                    content = reader.lines()
                        .skip(startLine - 1)
                        .collect(Collectors.joining("\n"));
                }
                
                return new ReadResult(content, totalLines);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + path + " - " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<String> readBase64(String path, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = resolvePath(path);
                byte[] bytes = Files.readAllBytes(filePath);
                return Base64.getEncoder().encodeToString(bytes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + path + " - " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getLineCount(String path, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = resolvePath(path);
                try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                    return (int) lines.count();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to count lines: " + path + " - " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String path, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            Path filePath = resolvePath(path);
            return Files.exists(filePath) && Files.isRegularFile(filePath);
        });
    }

    private Path resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        return Paths.get(cwd).resolve(path);
    }
}
