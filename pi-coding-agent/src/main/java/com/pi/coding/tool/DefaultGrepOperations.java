package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Default implementation of GrepOperations using local file system.
 * Uses Java regex for pattern matching (ripgrep not required).
 */
public class DefaultGrepOperations implements GrepOperations {

    private final String cwd;

    public DefaultGrepOperations(String cwd) {
        this.cwd = cwd;
    }

    @Override
    public CompletableFuture<Boolean> isDirectory(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> Files.isDirectory(Path.of(absolutePath)));
    }

    @Override
    public CompletableFuture<String> readFile(String absolutePath, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readString(Path.of(absolutePath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + absolutePath + " - " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<GrepResult> grep(String pattern, String searchPath, GrepOptions options, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            List<GrepMatch> matches = new ArrayList<>();
            boolean[] limitReached = {false};
            Path basePath = Path.of(searchPath);

            // Compile pattern
            Pattern regex;
            try {
                int flags = options.ignoreCase() ? Pattern.CASE_INSENSITIVE : 0;
                String regexPattern = options.literal() ? Pattern.quote(pattern) : pattern;
                regex = Pattern.compile(regexPattern, flags);
            } catch (PatternSyntaxException e) {
                throw new RuntimeException("Invalid regex pattern: " + e.getMessage(), e);
            }

            // Glob pattern matcher
            PathMatcher globMatcher = null;
            if (options.glob() != null && !options.glob().isEmpty()) {
                globMatcher = FileSystems.getDefault().getPathMatcher("glob:" + options.glob());
            }
            final PathMatcher finalGlobMatcher = globMatcher;

            try {
                if (Files.isDirectory(basePath)) {
                    Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (signal != null && signal.isCancelled()) {
                                return FileVisitResult.TERMINATE;
                            }
                            
                            String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
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
                            
                            if (matches.size() >= options.limit()) {
                                limitReached[0] = true;
                                return FileVisitResult.TERMINATE;
                            }

                            // Check glob pattern
                            if (finalGlobMatcher != null) {
                                Path relativePath = basePath.relativize(file);
                                if (!finalGlobMatcher.matches(relativePath) && !finalGlobMatcher.matches(file.getFileName())) {
                                    return FileVisitResult.CONTINUE;
                                }
                            }

                            // Search file
                            searchFile(file, regex, matches, options.limit());
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    // Single file
                    searchFile(basePath, regex, matches, options.limit());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to search: " + e.getMessage(), e);
            }

            return new GrepResult(matches, limitReached[0] || matches.size() >= options.limit());
        });
    }

    private void searchFile(Path file, Pattern regex, List<GrepMatch> matches, int limit) {
        try {
            // Skip binary files
            String contentType = Files.probeContentType(file);
            if (contentType != null && !contentType.startsWith("text/")) {
                return;
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size() && matches.size() < limit; i++) {
                String line = lines.get(i);
                Matcher matcher = regex.matcher(line);
                if (matcher.find()) {
                    matches.add(new GrepMatch(file.toString(), i + 1, line));
                }
            }
        } catch (IOException e) {
            // Skip files we can't read
        }
    }

    public String resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.toString();
        }
        return Paths.get(cwd).resolve(path).toString();
    }
}
