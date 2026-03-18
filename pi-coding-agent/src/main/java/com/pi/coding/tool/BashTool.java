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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Bash tool for executing shell commands.
 * <p>
 * Executes bash commands in the current working directory. Returns stdout and stderr.
 * Output is truncated to last maxLines or maxBytes (whichever is hit first).
 * If truncated, full output is saved to a temp file.
 * <p>
 * Validates: Requirements 8.1-8.11
 */
public class BashTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String cwd;
    private final BashOperations operations;
    private final TruncationOptions truncationOptions;

    /**
     * Creates a BashTool with default operations and truncation options.
     *
     * @param cwd Current working directory
     */
    public BashTool(String cwd) {
        this(cwd, new DefaultBashOperations(cwd), new TruncationOptions());
    }

    /**
     * Creates a BashTool with custom operations.
     *
     * @param cwd Current working directory
     * @param operations Custom bash operations
     */
    public BashTool(String cwd, BashOperations operations) {
        this(cwd, operations, new TruncationOptions());
    }

    /**
     * Creates a BashTool with custom operations and truncation options.
     *
     * @param cwd Current working directory
     * @param operations Custom bash operations
     * @param truncationOptions Truncation options
     */
    public BashTool(String cwd, BashOperations operations, TruncationOptions truncationOptions) {
        this.cwd = cwd;
        this.operations = operations;
        this.truncationOptions = truncationOptions;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return String.format(
            "Execute a bash command in the current working directory. Returns stdout and stderr. " +
            "Output is truncated to last %d lines or %dKB (whichever is hit first). " +
            "If truncated, full output is saved to a temp file. Optionally provide a timeout in seconds.",
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
        label.put("description", "Brief description of what this command does (shown to user)");

        ObjectNode command = properties.putObject("command");
        command.put("type", "string");
        command.put("description", "Bash command to execute");

        ObjectNode timeout = properties.putObject("timeout");
        timeout.put("type", "number");
        timeout.put("description", "Timeout in seconds (optional, no default timeout)");

        schema.putArray("required").add("label").add("command");

        return schema;
    }

    @Override
    public CompletableFuture<AgentToolResult<?>> execute(
            String toolCallId,
            JsonNode args,
            CancellationSignal signal,
            AgentToolUpdateCallback onUpdate) {

        String command = args.get("command").asText();
        Integer timeout = args.has("timeout") && !args.get("timeout").isNull()
            ? args.get("timeout").asInt() : null;

        return operations.execute(command, timeout, signal)
            .thenApply(result -> {
                String output = result.combinedOutput();
                long totalBytes = output.getBytes(StandardCharsets.UTF_8).length;

                // Write to temp file if output exceeds limit
                String tempFilePath = null;
                if (totalBytes > truncationOptions.maxBytes()) {
                    tempFilePath = writeTempFile(output);
                }

                // Apply tail truncation
                TruncationResult truncation = Truncation.truncateTail(output, truncationOptions);
                String outputText = truncation.content().isEmpty() ? "(no output)" : truncation.content();

                BashToolDetails details = null;

                if (truncation.truncated()) {
                    // Ensure temp file exists for truncated output
                    if (tempFilePath == null) {
                        tempFilePath = writeTempFile(output);
                    }

                    details = BashToolDetails.truncated(command, result.exitCode(), tempFilePath, truncation);

                    // Build actionable notice
                    int startLine = truncation.totalLines() - truncation.outputLines() + 1;
                    int endLine = truncation.totalLines();

                    if (truncation.lastLinePartial()) {
                        // Edge case: last line alone > maxBytes
                        String[] lines = output.split("\n");
                        String lastLine = lines.length > 0 ? lines[lines.length - 1] : "";
                        String lastLineSize = Truncation.formatSize(
                            lastLine.getBytes(StandardCharsets.UTF_8).length);
                        outputText += String.format(
                            "\n\n[Showing last %s of line %d (line is %s). Full output: %s]",
                            Truncation.formatSize(truncation.outputBytes()),
                            endLine, lastLineSize, tempFilePath);
                    } else if ("lines".equals(truncation.truncatedBy())) {
                        outputText += String.format(
                            "\n\n[Showing lines %d-%d of %d. Full output: %s]",
                            startLine, endLine, truncation.totalLines(), tempFilePath);
                    } else {
                        outputText += String.format(
                            "\n\n[Showing lines %d-%d of %d (%s limit). Full output: %s]",
                            startLine, endLine, truncation.totalLines(),
                            Truncation.formatSize(truncationOptions.maxBytes()), tempFilePath);
                    }
                } else {
                    details = BashToolDetails.simple(command, result.exitCode(), totalBytes);
                }

                // Check exit code
                if (result.exitCode() != 0) {
                    String errorMessage = String.format(
                        "%s\n\nCommand exited with code %d",
                        outputText.trim(), result.exitCode());
                    throw new RuntimeException(errorMessage);
                }

                List<UserContentBlock> content = List.of(new TextContent(outputText));
                return new AgentToolResult<>(content, details);
            });
    }

    /**
     * Generate a unique temp file path for bash output.
     */
    private String getTempFilePath() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        return System.getProperty("java.io.tmpdir") + "/pi-bash-" + id + ".log";
    }

    /**
     * Write output to a temp file.
     */
    private String writeTempFile(String output) {
        String tempFilePath = getTempFilePath();
        try {
            Files.writeString(Path.of(tempFilePath), output, StandardCharsets.UTF_8);
            return tempFilePath;
        } catch (IOException e) {
            // Log but don't fail - temp file is optional
            return null;
        }
    }
}
