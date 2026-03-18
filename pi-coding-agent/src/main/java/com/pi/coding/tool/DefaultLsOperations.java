package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Default implementation of LsOperations using local file system.
 */
public class DefaultLsOperations implements LsOperations {

    private final String cwd;

    public DefaultLsOperations(String cwd) {
        this.cwd = cwd;
    }

    @Override
    public CompletableFuture<Boolean> exists(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> Files.exists(Path.of(absolutePath)));
    }

    @Override
    public CompletableFuture<Boolean> isDirectory(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> Files.isDirectory(Path.of(absolutePath)));
    }

    @Override
    public CompletableFuture<List<DirEntry>> readdir(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try (Stream<Path> stream = Files.list(Path.of(absolutePath))) {
                List<DirEntry> entries = new ArrayList<>();
                stream.forEach(path -> {
                    entries.add(new DirEntry(
                        path.getFileName().toString(),
                        Files.isDirectory(path)
                    ));
                });
                return entries;
            } catch (IOException e) {
                throw new RuntimeException("Failed to list directory: " + absolutePath + " - " + e.getMessage(), e);
            }
        });
    }

    public String resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.toString();
        }
        return Paths.get(cwd).resolve(path).toString();
    }
}
