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
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Grep tool for searching file contents.
 * <p>
 * Searches for patterns in files and returns matching lines with file paths and line numbers.
 * Respects .gitignore. Output is truncated to limit matches or maxBytes.
 * <p>
 * Validates: Requirements 11.1-11.7
 */
public class GrepTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LINE_LENGTH = 500;

    private final String cwd;
    private final GrepOperations operations;
    private final TruncationOptions truncationOptions;

    public GrepTool(String cwd) {
        this(cwd, new DefaultGrepOperations(cwd), new TruncationOptions());
    }

    public GrepTool(String cwd, GrepOperations operations) {
        this(cwd, operations, new TruncationOptions());
    }

    public GrepTool(String cwd, GrepOperations operations, TruncationOptions truncationOptions) {
        this.cwd = cwd;
        this.operations = operations;
        this.truncationOptions = truncationOptions;
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return String.format(
            "Search file contents for a pattern. Returns matching lines with file paths and line numbers. " +
            "Respects .gitignore. Output is truncated to %d matches or %dKB (whichever is hit first). " +
            "Long lines are truncated to %d chars.",
            DEFAULT_LIMIT, truncationOptions.maxBytes() / 1024, MAX_LINE_LENGTH
        );
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode pattern = properties.putObject("pattern");
        pattern.put("type", "string");
        pattern.put("description", "Search pattern (regex or literal string)");

        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Directory or file to search (default: current directory)");

        ObjectNode glob = properties.putObject("glob");
        glob.put("type", "string");
        glob.put("description", "Filter files by glob pattern, e.g. '*.ts' or '**/*.spec.ts'");

        ObjectNode ignoreCase = properties.putObject("ignoreCase");
        ignoreCase.put("type", "boolean");
        ignoreCase.put("description", "Case-insensitive search (default: false)");

        ObjectNode literal = properties.putObject("literal");
        literal.put("type", "boolean");
        literal.put("description", "Treat pattern as literal string instead of regex (default: false)");

        ObjectNode context = properties.putObject("context");
        context.put("type", "number");
        context.put("description", "Number of lines to show before and after each match (default: 0)");

        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "number");
        limit.put("description", "Maximum number of matches to return (default: 100)");

        schema.putArray("required").add("pattern");

        return schema;
    }

    @Override
    public CompletableFuture<AgentToolResult<?>> execute(
            String toolCallId,
            JsonNode args,
            CancellationSignal signal,
            AgentToolUpdateCallback onUpdate) {

        String pattern = args.get("pattern").asText();
        String searchDir = args.has("path") && !args.get("path").isNull() 
            ? args.get("path").asText() : ".";
        String glob = args.has("glob") && !args.get("glob").isNull() 
            ? args.get("glob").asText() : null;
        boolean ignoreCase = args.has("ignoreCase") && args.get("ignoreCase").asBoolean();
        boolean literal = args.has("literal") && args.get("literal").asBoolean();
        int context = args.has("context") && !args.get("context").isNull() 
            ? args.get("context").asInt() : 0;
        int limit = args.has("limit") && !args.get("limit").isNull() 
            ? args.get("limit").asInt() : DEFAULT_LIMIT;

        String searchPath = resolvePath(searchDir);

        if (signal != null && signal.isCancelled()) {
            return CompletableFuture.failedFuture(new RuntimeException("Operation aborted"));
        }

        GrepOperations.GrepOptions options = new GrepOperations.GrepOptions(
            glob, ignoreCase, literal, context, limit
        );

        return operations.grep(pattern, searchPath, options, signal)
            .thenCompose(result -> {
                if (result.matches().isEmpty()) {
                    List<UserContentBlock> content = List.of(new TextContent("No matches found"));
                    return CompletableFuture.completedFuture(new AgentToolResult<>(content, null));
                }

                // Format matches with context
                return formatMatches(result, searchPath, context, signal);
            });
    }

    private CompletableFuture<AgentToolResult<?>> formatMatches(
            GrepOperations.GrepResult result, String searchPath, int context, CancellationSignal signal) {
        
        List<String> outputLines = new ArrayList<>();
        boolean[] linesTruncated = {false};
        Path basePath = Paths.get(searchPath);

        for (GrepOperations.GrepMatch match : result.matches()) {
            String relativePath = formatPath(match.filePath(), basePath);
            String lineContent = match.lineContent();

            // Truncate long lines
            if (lineContent.length() > MAX_LINE_LENGTH) {
                lineContent = lineContent.substring(0, MAX_LINE_LENGTH) + "...";
                linesTruncated[0] = true;
            }

            outputLines.add(String.format("%s:%d: %s", relativePath, match.lineNumber(), lineContent));
        }

        // Apply byte truncation
        String rawOutput = String.join("\n", outputLines);
        TruncationResult truncation = Truncation.truncateHead(rawOutput, 
            new TruncationOptions(Integer.MAX_VALUE, truncationOptions.maxBytes()));

        String output = truncation.content();
        List<String> notices = new ArrayList<>();

        if (result.limitReached()) {
            notices.add(String.format("%d matches limit reached. Use limit=%d for more, or refine pattern",
                result.matches().size(), result.matches().size() * 2));
        }

        if (truncation.truncated()) {
            notices.add(String.format("%s limit reached", Truncation.formatSize(truncationOptions.maxBytes())));
        }

        if (linesTruncated[0]) {
            notices.add(String.format("Some lines truncated to %d chars. Use read tool to see full lines", MAX_LINE_LENGTH));
        }

        if (!notices.isEmpty()) {
            output += "\n\n[" + String.join(". ", notices) + "]";
        }

        GrepToolDetails details = new GrepToolDetails(
            "", result.matches().size(), truncation.truncated(),
            result.limitReached() ? result.matches().size() : null, linesTruncated[0]
        );

        List<UserContentBlock> content = List.of(new TextContent(output));
        return CompletableFuture.completedFuture(new AgentToolResult<>(content, details));
    }

    private String formatPath(String filePath, Path basePath) {
        try {
            Path path = Paths.get(filePath);
            if (path.startsWith(basePath)) {
                return basePath.relativize(path).toString().replace('\\', '/');
            }
            return path.getFileName().toString();
        } catch (Exception e) {
            return filePath;
        }
    }

    private String resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.toString();
        }
        return Paths.get(cwd).resolve(path).toString();
    }
}
