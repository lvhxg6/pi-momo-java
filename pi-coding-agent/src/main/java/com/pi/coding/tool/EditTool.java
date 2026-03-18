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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Edit tool for replacing exact text in files.
 * <p>
 * Uses fuzzy matching to find text (tries exact match first, then fuzzy).
 * Preserves original line endings (CRLF or LF) and handles BOM correctly.
 * <p>
 * Validates: Requirements 9.1-9.10
 */
public class EditTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String cwd;
    private final EditOperations operations;

    /**
     * Creates an EditTool with default operations.
     *
     * @param cwd Current working directory
     */
    public EditTool(String cwd) {
        this(cwd, new DefaultEditOperations(cwd));
    }

    /**
     * Creates an EditTool with custom operations.
     *
     * @param cwd Current working directory
     * @param operations Custom edit operations
     */
    public EditTool(String cwd, EditOperations operations) {
        this.cwd = cwd;
        this.operations = operations;
    }

    @Override
    public String name() {
        return "edit";
    }

    @Override
    public String description() {
        return "Edit a file by replacing exact text. The oldText must match exactly (including whitespace). " +
               "Use this for precise, surgical edits.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Path to the file to edit (relative or absolute)");

        ObjectNode oldText = properties.putObject("oldText");
        oldText.put("type", "string");
        oldText.put("description", "Exact text to find and replace (must match exactly)");

        ObjectNode newText = properties.putObject("newText");
        newText.put("type", "string");
        newText.put("description", "New text to replace the old text with");

        schema.putArray("required").add("path").add("oldText").add("newText");

        return schema;
    }

    @Override
    public CompletableFuture<AgentToolResult<?>> execute(
            String toolCallId,
            JsonNode args,
            CancellationSignal signal,
            AgentToolUpdateCallback onUpdate) {

        String path = args.get("path").asText();
        String oldText = args.get("oldText").asText();
        String newText = args.get("newText").asText();

        String absolutePath = resolvePath(path);

        // Check if file exists
        return operations.access(absolutePath, signal)
            .thenCompose(accessible -> {
                if (!accessible) {
                    throw new RuntimeException("File not found: " + path);
                }

                // Check cancellation
                if (signal != null && signal.isCancelled()) {
                    throw new RuntimeException("Operation aborted");
                }

                // Read the file
                return operations.readFile(absolutePath, signal);
            })
            .thenCompose(buffer -> {
                // Check cancellation
                if (signal != null && signal.isCancelled()) {
                    throw new RuntimeException("Operation aborted");
                }

                String rawContent = new String(buffer, StandardCharsets.UTF_8);

                // Strip BOM before matching
                EditDiff.BomResult bomResult = EditDiff.stripBom(rawContent);
                String bom = bomResult.bom();
                String content = bomResult.text();

                String originalEnding = EditDiff.detectLineEnding(content);
                String normalizedContent = EditDiff.normalizeToLF(content);
                String normalizedOldText = EditDiff.normalizeToLF(oldText);
                String normalizedNewText = EditDiff.normalizeToLF(newText);

                // Find the old text using fuzzy matching
                EditDiff.FuzzyMatchResult matchResult = EditDiff.fuzzyFindText(normalizedContent, normalizedOldText);

                if (!matchResult.found()) {
                    throw new RuntimeException(
                        "Could not find the exact text in " + path + 
                        ". The old text must match exactly including all whitespace and newlines.");
                }

                // Count occurrences using fuzzy-normalized content
                String fuzzyContent = EditDiff.normalizeForFuzzyMatch(normalizedContent);
                String fuzzyOldText = EditDiff.normalizeForFuzzyMatch(normalizedOldText);
                int occurrences = countOccurrences(fuzzyContent, fuzzyOldText);

                if (occurrences > 1) {
                    throw new RuntimeException(
                        "Found " + occurrences + " occurrences of the text in " + path + 
                        ". The text must be unique. Please provide more context to make it unique.");
                }

                // Check cancellation
                if (signal != null && signal.isCancelled()) {
                    throw new RuntimeException("Operation aborted");
                }

                // Perform replacement
                String baseContent = matchResult.contentForReplacement();
                String newContent = baseContent.substring(0, matchResult.index()) +
                    normalizedNewText +
                    baseContent.substring(matchResult.index() + matchResult.matchLength());

                // Verify the replacement actually changed something
                if (baseContent.equals(newContent)) {
                    throw new RuntimeException(
                        "No changes made to " + path + 
                        ". The replacement produced identical content. " +
                        "This might indicate an issue with special characters or the text not existing as expected.");
                }

                String finalContent = bom + EditDiff.restoreLineEndings(newContent, originalEnding);

                // Generate diff before writing
                EditDiff.DiffResult diffResult = EditDiff.generateDiffString(baseContent, newContent);

                // Write the file
                return operations.writeFile(absolutePath, finalContent, signal)
                    .thenApply(v -> {
                        // Check cancellation
                        if (signal != null && signal.isCancelled()) {
                            throw new RuntimeException("Operation aborted");
                        }

                        List<UserContentBlock> resultContent = List.of(
                            new TextContent("Successfully replaced text in " + path + ".")
                        );
                        EditToolDetails details = new EditToolDetails(
                            path, diffResult.firstChangedLine(), diffResult.diff());
                        return new AgentToolResult<>(resultContent, details);
                    });
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

    /**
     * Count occurrences of a substring in a string.
     */
    private static int countOccurrences(String str, String sub) {
        if (sub.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
