package com.pi.coding.prompt;

import com.pi.coding.resource.ContextFile;
import com.pi.coding.resource.Skill;
import com.pi.coding.resource.Skills;

import java.time.LocalDate;
import java.util.*;

/**
 * Builds the system prompt with tools, guidelines, skills, and context.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li>Custom prompt: replaces the default prompt entirely</li>
 *   <li>Default prompt: includes agent identity, tools, guidelines, docs references</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 24.1-24.8</b>
 */
public final class SystemPromptBuilder {

    /** Built-in tool descriptions. */
    private static final Map<String, String> TOOL_DESCRIPTIONS = Map.of(
            "read", "Read file contents",
            "bash", "Execute bash commands (ls, grep, find, etc.)",
            "edit", "Make surgical edits to files (find exact text and replace)",
            "write", "Create or overwrite files",
            "grep", "Search file contents for patterns (respects .gitignore)",
            "find", "Find files by glob pattern (respects .gitignore)",
            "ls", "List directory contents"
    );

    private SystemPromptBuilder() {
        // Utility class
    }

    /**
     * Build the system prompt from the given configuration.
     *
     * @param config the system prompt configuration
     * @return the assembled system prompt string
     */
    public static String buildSystemPrompt(SystemPromptConfig config) {
        String cwd = config.cwd().replace("\\", "/");
        String date = LocalDate.now().toString();
        String appendSection = config.appendSystemPrompt() != null
                ? "\n\n" + config.appendSystemPrompt() : "";

        if (config.customPrompt() != null) {
            return buildCustomPrompt(config, cwd, date, appendSection);
        }
        return buildDefaultPrompt(config, cwd, date, appendSection);
    }

    /**
     * Build prompt when a custom system prompt is provided (replaces default).
     */
    private static String buildCustomPrompt(SystemPromptConfig config, String cwd, String date, String appendSection) {
        StringBuilder prompt = new StringBuilder(config.customPrompt());

        if (!appendSection.isEmpty()) {
            prompt.append(appendSection);
        }

        appendContextFiles(prompt, config.contextFiles());

        // Append skills if read tool is available
        boolean hasRead = config.selectedTools().isEmpty() || config.selectedTools().contains("read");
        if (hasRead && !config.skills().isEmpty()) {
            prompt.append(Skills.formatSkillsForPrompt(config.skills()));
        }

        prompt.append("\nCurrent date: ").append(date);
        prompt.append("\nCurrent working directory: ").append(cwd);

        return prompt.toString();
    }

    /**
     * Build the default system prompt with agent identity, tools, and guidelines.
     */
    private static String buildDefaultPrompt(SystemPromptConfig config, String cwd, String date, String appendSection) {
        List<String> tools = config.selectedTools();

        // Build tools list
        String toolsList = buildToolsList(tools, config.toolSnippets());

        // Build guidelines
        String guidelines = buildGuidelines(tools, config.promptGuidelines());

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert coding assistant operating inside pi, a coding agent harness. ");
        prompt.append("You help users by reading files, executing commands, editing code, and writing new files.\n\n");
        prompt.append("Available tools:\n").append(toolsList).append("\n\n");
        prompt.append("In addition to the tools above, you may have access to other custom tools depending on the project.\n\n");
        prompt.append("Guidelines:\n").append(guidelines);

        if (!appendSection.isEmpty()) {
            prompt.append(appendSection);
        }

        appendContextFiles(prompt, config.contextFiles());

        // Append skills if read tool is available
        if (tools.contains("read") && !config.skills().isEmpty()) {
            prompt.append(Skills.formatSkillsForPrompt(config.skills()));
        }

        prompt.append("\nCurrent date: ").append(date);
        prompt.append("\nCurrent working directory: ").append(cwd);

        return prompt.toString();
    }

    /**
     * Build the tools list section.
     */
    private static String buildToolsList(List<String> tools, Map<String, String> toolSnippets) {
        List<String> lines = new ArrayList<>();
        for (String name : tools) {
            String snippet = toolSnippets.getOrDefault(name, TOOL_DESCRIPTIONS.get(name));
            if (snippet != null) {
                lines.add("- " + name + ": " + snippet);
            }
        }
        return lines.isEmpty() ? "(none)" : String.join("\n", lines);
    }

    /**
     * Build the guidelines section based on available tools.
     */
    private static String buildGuidelines(List<String> tools, List<String> extraGuidelines) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> guidelines = new ArrayList<>();

        boolean hasBash = tools.contains("bash");
        boolean hasEdit = tools.contains("edit");
        boolean hasWrite = tools.contains("write");
        boolean hasGrep = tools.contains("grep");
        boolean hasFind = tools.contains("find");
        boolean hasLs = tools.contains("ls");
        boolean hasRead = tools.contains("read");

        // File exploration guidelines
        if (hasBash && !hasGrep && !hasFind && !hasLs) {
            addGuideline(guidelines, seen, "Use bash for file operations like ls, rg, find");
        } else if (hasBash && (hasGrep || hasFind || hasLs)) {
            addGuideline(guidelines, seen,
                    "Prefer grep/find/ls tools over bash for file exploration (faster, respects .gitignore)");
        }

        if (hasRead && hasEdit) {
            addGuideline(guidelines, seen,
                    "Use read to examine files before editing. You must use this tool instead of cat or sed.");
        }

        if (hasEdit) {
            addGuideline(guidelines, seen, "Use edit for precise changes (old text must match exactly)");
        }

        if (hasWrite) {
            addGuideline(guidelines, seen, "Use write only for new files or complete rewrites");
        }

        if (hasEdit || hasWrite) {
            addGuideline(guidelines, seen,
                    "When summarizing your actions, output plain text directly - do NOT use cat or bash to display what you did");
        }

        // Extra guidelines from config
        for (String g : extraGuidelines) {
            String normalized = g.trim();
            if (!normalized.isEmpty()) {
                addGuideline(guidelines, seen, normalized);
            }
        }

        // Always include these
        addGuideline(guidelines, seen, "Be concise in your responses");
        addGuideline(guidelines, seen, "Show file paths clearly when working with files");

        return guidelines.stream()
                .map(g -> "- " + g)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static void addGuideline(List<String> guidelines, Set<String> seen, String guideline) {
        if (seen.add(guideline)) {
            guidelines.add(guideline);
        }
    }

    /**
     * Append context files section to the prompt.
     */
    private static void appendContextFiles(StringBuilder prompt, List<ContextFile> contextFiles) {
        if (contextFiles.isEmpty()) return;

        prompt.append("\n\n# Project Context\n\n");
        prompt.append("Project-specific instructions and guidelines:\n\n");
        for (ContextFile cf : contextFiles) {
            prompt.append("## ").append(cf.path()).append("\n\n");
            prompt.append(cf.content()).append("\n\n");
        }
    }
}
