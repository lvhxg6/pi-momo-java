package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of FindOperations using local file system.
 */
public class DefaultFindOperations implements FindOperations {

    private final String cwd;

    public DefaultFindOperations(String cwd) {
        this.cwd = cwd;
    }

    @Override
    public CompletableFuture<Boolean> exists(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> Files.exists(Path.of(absolutePath)));
    }

    @Override
    public CompletableFuture<List<String>> glob(String pattern, String searchPath, List<String> ignore, int limit, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            Path basePath = Path.of(searchPath);

            try {
                // Convert glob pattern to PathMatcher
                String globPattern = "glob:" + pattern;
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

                Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (signal != null && signal.isCancelled()) {
                            return FileVisitResult.TERMINATE;
                        }
                        
                        String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        // Skip ignored directories
                        if (dirName.equals("node_modules") || dirName.equals(".git")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (signal != null && signal.isCancelled()) {
                            return FileVisitResult.TERMINATE;
                        }
                        
                        if (results.size() >= limit) {
                            return FileVisitResult.TERMINATE;
                        }

                        Path relativePath = basePath.relativize(file);
                        if (matcher.matches(relativePath) || matcher.matches(file.getFileName())) {
                            results.add(relativePath.toString().replace('\\', '/'));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        // Skip files we can't access
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to search files: " + e.getMessage(), e);
            }

            return results;
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
