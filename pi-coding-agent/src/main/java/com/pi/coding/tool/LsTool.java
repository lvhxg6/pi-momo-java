package com.pi.coding.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.AgentToolResult;
import com.pi.agent.types.AgentToolUpdateCallback;
import com.pi.ai.core.types.CancellationSignal;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.UserContentBlock;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Ls tool for listing directory contents.
 * <p>
 * Returns entries sorted alphabetically, with '/' suffix for directories.
 * Includes dotfiles. Output is truncated to limit entries or maxBytes.
 * <p>
 * Validates: Requirements 13.1-13.6
 */
public class LsTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 500;

    private final String cwd;
    private final LsOperations operations;
    private final TruncationOptions truncationOptions;

    public LsTool(String cwd) {
        this(cwd, new DefaultLsOperations(cwd), new TruncationOptions());
    }

    public LsTool(String cwd, LsOperations operations) {
        this(cwd, operations, new TruncationOptions());
    }

    public LsTool(String cwd, LsOperations operations, TruncationOptions truncationOptions) {
        this.cwd = cwd;
        this.operations = operations;
        this.truncationOptions = truncationOptions;
    }

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String description() {
        return String.format(
            "List directory contents. Returns entries sorted alphabetically, with '/' suffix for directories. " +
            "Includes dotfiles. Output is truncated to %d entries or %dKB (whichever is hit first).",
            DEFAULT_LIMIT, truncationOptions.maxBytes() / 1024
        );
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Directory to list (default: current directory)");

        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "number");
        limit.put("description", "Maximum number of entries to return (default: 500)");

        // No required fields
        schema.putArray("required");

        return schema;
    }

    @Override
    public CompletableFuture<AgentToolResult<?>> execute(
            String toolCallId,
            JsonNode args,
            CancellationSignal signal,
            AgentToolUpdateCallback onUpdate) {

        String dirPath = args.has("path") && !args.get("path").isNull() 
            ? args.get("path").asText() : ".";
        int limit = args.has("limit") && !args.get("limit").isNull() 
            ? args.get("limit").asInt() : DEFAULT_LIMIT;

        String absolutePath = resolvePath(dirPath);

        if (signal != null && signal.isCancelled()) {
            return CompletableFuture.failedFuture(new RuntimeException("Operation aborted"));
        }

        return operations.exists(absolutePath, signal)
            .thenCompose(exists -> {
                if (!exists) {
                    throw new RuntimeException("Path not found: " + dirPath);
                }
                return operations.isDirectory(absolutePath, signal);
            })
            .thenCompose(isDir -> {
                if (!isDir) {
                    throw new RuntimeException("Not a directory: " + dirPath);
                }
                return operations.readdir(absolutePath, signal);
            })
            .thenApply(entries -> {
                if (entries.isEmpty()) {
                    List<UserContentBlock> content = List.of(new TextContent("(empty directory)"));
                    return new AgentToolResult<>(content, null);
                }

                // Sort alphabetically (case-insensitive)
                entries.sort(Comparator.comparing(e -> e.name().toLowerCase()));

                // Format entries with directory indicators
                List<String> results = new ArrayList<>();
                boolean entryLimitReached = false;

                for (LsOperations.DirEntry entry : entries) {
                    if (results.size() >= limit) {
                        entryLimitReached = true;
                        break;
                    }
                    String suffix = entry.isDirectory() ? "/" : "";
                    results.add(entry.name() + suffix);
                }

                String rawOutput = String.join("\n", results);

                // Apply byte truncation
                TruncationResult truncation = Truncation.truncateHead(rawOutput, 
                    new TruncationOptions(Integer.MAX_VALUE, truncationOptions.maxBytes()));

                String output = truncation.content();
                List<String> notices = new ArrayList<>();

                if (entryLimitReached) {
                    notices.add(String.format("%d entries limit reached. Use limit=%d for more",
                        limit, limit * 2));
                }

                if (truncation.truncated()) {
                    notices.add(String.format("%s limit reached", Truncation.formatSize(truncationOptions.maxBytes())));
                }

                if (!notices.isEmpty()) {
                    output += "\n\n[" + String.join(". ", notices) + "]";
                }

                LsToolDetails details = new LsToolDetails(
                    dirPath, results.size(), truncation.truncated(),
                    entryLimitReached ? limit : null
                );

                List<UserContentBlock> content = List.of(new TextContent(output));
                return new AgentToolResult<>(content, details);
            });
    }

    private String resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.toString();
        }
        return Paths.get(cwd).resolve(path).toString();
    }
}
