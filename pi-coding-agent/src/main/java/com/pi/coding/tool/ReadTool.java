package com.pi.coding.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.AgentToolResult;
import com.pi.agent.types.AgentToolUpdateCallback;
import com.pi.ai.core.types.CancellationSignal;
import com.pi.ai.core.types.ImageContent;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.UserContentBlock;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Read tool for reading file contents.
 * <p>
 * Supports text files and images (jpg, png, gif, webp). Images are sent as attachments.
 * For text files, output is truncated to maxLines or maxBytes (whichever is hit first).
 * Use offset/limit for large files.
 * <p>
 * Validates: Requirements 7.1-7.9
 */
public class ReadTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Map of file extensions to MIME types for common image formats.
     */
    private static final Map<String, String> IMAGE_MIME_TYPES = Map.of(
        ".jpg", "image/jpeg",
        ".jpeg", "image/jpeg",
        ".png", "image/png",
        ".gif", "image/gif",
        ".webp", "image/webp"
    );

    private final String cwd;
    private final ReadOperations operations;
    private final TruncationOptions truncationOptions;

    /**
     * Creates a ReadTool with default operations and truncation options.
     *
     * @param cwd Current working directory
     */
    public ReadTool(String cwd) {
        this(cwd, new DefaultReadOperations(cwd), new TruncationOptions());
    }

    /**
     * Creates a ReadTool with custom operations.
     *
     * @param cwd Current working directory
     * @param operations Custom read operations
     */
    public ReadTool(String cwd, ReadOperations operations) {
        this(cwd, operations, new TruncationOptions());
    }

    /**
     * Creates a ReadTool with custom operations and truncation options.
     *
     * @param cwd Current working directory
     * @param operations Custom read operations
     * @param truncationOptions Truncation options
     */
    public ReadTool(String cwd, ReadOperations operations, TruncationOptions truncationOptions) {
        this.cwd = cwd;
        this.operations = operations;
        this.truncationOptions = truncationOptions;
    }

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String description() {
        return String.format(
            "Read the contents of a file. Supports text files and images (jpg, png, gif, webp). " +
            "Images are sent as attachments. For text files, output is truncated to %d lines or %dKB " +
            "(whichever is hit first). Use offset/limit for large files.",
            truncationOptions.maxLines(),
            truncationOptions.maxBytes() / 1024
        );
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode label = properties.putObject("label");
        label.put("type", "string");
        label.put("description", "Brief description of what you're reading and why (shown to user)");

        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Path to the file to read (relative or absolute)");

        ObjectNode offset = properties.putObject("offset");
        offset.put("type", "number");
        offset.put("description", "Line number to start reading from (1-indexed)");

        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "number");
        limit.put("description", "Maximum number of lines to read");

        schema.putArray("required").add("label").add("path");

        return schema;
    }

    @Override
    public CompletableFuture<AgentToolResult<?>> execute(
            String toolCallId,
            JsonNode args,
            CancellationSignal signal,
            AgentToolUpdateCallback onUpdate) {

        String path = args.get("path").asText();
        Integer offset = args.has("offset") && !args.get("offset").isNull()
            ? args.get("offset").asInt() : null;
        Integer limit = args.has("limit") && !args.get("limit").isNull()
            ? args.get("limit").asInt() : null;

        String mimeType = getImageMimeType(path);

        if (mimeType != null) {
            // Read as image
            return readImage(path, mimeType, signal);
        } else {
            // Read as text
            return readText(path, offset, limit, signal);
        }
    }

    private CompletableFuture<AgentToolResult<?>> readImage(
            String path, String mimeType, CancellationSignal signal) {
        return operations.readBase64(path, signal)
            .thenApply(base64 -> {
                List<UserContentBlock> content = List.of(
                    new TextContent("Read image file [" + mimeType + "]"),
                    new ImageContent(base64, mimeType)
                );
                ReadToolDetails details = ReadToolDetails.forImage(path, mimeType);
                return new AgentToolResult<>(content, details);
            });
    }

    private CompletableFuture<AgentToolResult<?>> readText(
            String path, Integer offset, Integer limit, CancellationSignal signal) {
        
        int startLine = offset != null ? Math.max(1, offset) : 1;

        return operations.readText(path, offset, signal)
            .thenApply(result -> {
                int totalFileLines = result.totalLines();
                String selectedContent = result.content();

                // Check if offset is out of bounds
                if (startLine > totalFileLines) {
                    throw new RuntimeException(
                        String.format("Offset %d is beyond end of file (%d lines total)", 
                            offset, totalFileLines));
                }

                Integer userLimitedLines = null;

                // Apply user limit if specified
                if (limit != null) {
                    String[] lines = selectedContent.split("\n", -1);
                    int endLine = Math.min(limit, lines.length);
                    selectedContent = String.join("\n", 
                        java.util.Arrays.copyOfRange(lines, 0, endLine));
                    userLimitedLines = endLine;
                }

                // Apply truncation
                TruncationResult truncation = Truncation.truncateHead(selectedContent, truncationOptions);

                String outputText;
                ReadToolDetails details;

                if (truncation.firstLineExceedsLimit()) {
                    // First line at offset exceeds limit
                    String[] lines = selectedContent.split("\n", 2);
                    String firstLineSize = Truncation.formatSize(
                        lines[0].getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                    outputText = String.format(
                        "[Line %d is %s, exceeds %s limit. Use bash: sed -n '%dp' %s | head -c %d]",
                        startLine, firstLineSize, 
                        Truncation.formatSize(truncationOptions.maxBytes()),
                        startLine, path, truncationOptions.maxBytes());
                    details = ReadToolDetails.forText(path, totalFileLines, 0, null, truncation);
                } else if (truncation.truncated()) {
                    // Truncation occurred
                    int endLineDisplay = startLine + truncation.outputLines() - 1;
                    int nextOffset = endLineDisplay + 1;

                    outputText = truncation.content();

                    if ("lines".equals(truncation.truncatedBy())) {
                        outputText += String.format(
                            "\n\n[Showing lines %d-%d of %d. Use offset=%d to continue]",
                            startLine, endLineDisplay, totalFileLines, nextOffset);
                    } else {
                        outputText += String.format(
                            "\n\n[Showing lines %d-%d of %d (%s limit). Use offset=%d to continue]",
                            startLine, endLineDisplay, totalFileLines,
                            Truncation.formatSize(truncationOptions.maxBytes()), nextOffset);
                    }
                    details = ReadToolDetails.forText(path, totalFileLines, 
                        truncation.outputLines(), nextOffset, truncation);
                } else if (userLimitedLines != null) {
                    // User specified limit
                    int linesFromStart = startLine - 1 + userLimitedLines;
                    if (linesFromStart < totalFileLines) {
                        int remaining = totalFileLines - linesFromStart;
                        int nextOffset = startLine + userLimitedLines;

                        outputText = truncation.content();
                        outputText += String.format(
                            "\n\n[%d more lines in file. Use offset=%d to continue]",
                            remaining, nextOffset);
                        details = ReadToolDetails.forText(path, totalFileLines, 
                            userLimitedLines, nextOffset, truncation);
                    } else {
                        outputText = truncation.content();
                        details = ReadToolDetails.forText(path, totalFileLines, 
                            userLimitedLines, null, truncation);
                    }
                } else {
                    // No truncation
                    outputText = truncation.content();
                    details = ReadToolDetails.forText(path, totalFileLines, 
                        truncation.outputLines(), null, truncation);
                }

                List<UserContentBlock> content = List.of(new TextContent(outputText));
                return new AgentToolResult<>(content, details);
            });
    }

    /**
     * Check if a file is an image based on its extension.
     *
     * @param filePath File path
     * @return MIME type if image, null otherwise
     */
    private static String getImageMimeType(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex < 0) {
            return null;
        }
        String ext = filePath.substring(dotIndex).toLowerCase(Locale.ROOT);
        return IMAGE_MIME_TYPES.get(ext);
    }
}
