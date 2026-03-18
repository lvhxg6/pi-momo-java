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
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Write tool for writing content to files.
 * <p>
 * Creates the file if it doesn't exist, overwrites if it does.
 * Automatically creates parent directories.
 * <p>
 * Validates: Requirements 10.1-10.4
 */
public class WriteTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String cwd;
    private final WriteOperations operations;

    /**
     * Creates a WriteTool with default operations.
     *
     * @param cwd Current working directory
     */
    public WriteTool(String cwd) {
        this(cwd, new DefaultWriteOperations(cwd));
    }

    /**
     * Creates a WriteTool with custom operations.
     *
     * @param cwd Current working directory
     * @param operations Custom write operations
     */
    public WriteTool(String cwd, WriteOperations operations) {
        this.cwd = cwd;
        this.operations = operations;
    }

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String description() {
        return "Write content to a file. Creates the file if it doesn't exist, overwrites if it does. " +
               "Automatically creates parent directories.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Path to the file to write (relative or absolute)");

        ObjectNode content = properties.putObject("content");
        content.put("type", "string");
        content.put("description", "Content to write to the file");

        schema.putArray("required").add("path").add("content");

        return schema;
    }

    @Override
    public CompletableFuture<AgentToolResult<?>> execute(
            String toolCallId,
            JsonNode args,
            CancellationSignal signal,
            AgentToolUpdateCallback onUpdate) {

        String path = args.get("path").asText();
        String content = args.get("content").asText();

        String absolutePath = resolvePath(path);
        String dir = Paths.get(absolutePath).getParent().toString();

        // Check if already aborted
        if (signal != null && signal.isCancelled()) {
            return CompletableFuture.failedFuture(new RuntimeException("Operation aborted"));
        }

        // Create parent directories if needed
        return operations.mkdir(dir, signal)
            .thenCompose(v -> {
                // Check if aborted before writing
                if (signal != null && signal.isCancelled()) {
                    throw new RuntimeException("Operation aborted");
                }

                // Write the file
                return operations.writeFile(absolutePath, content, signal);
            })
            .thenApply(v -> {
                // Check if aborted after writing
                if (signal != null && signal.isCancelled()) {
                    throw new RuntimeException("Operation aborted");
                }

                List<UserContentBlock> resultContent = List.of(
                    new TextContent("Successfully wrote " + content.length() + " bytes to " + path)
                );
                return new AgentToolResult<>(resultContent, null);
            });
    }

    /**
     * Resolve a path relative to the working directory.
     */
    private String resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.toString();
        }
        return Paths.get(cwd).resolve(path).toString();
    }
}
