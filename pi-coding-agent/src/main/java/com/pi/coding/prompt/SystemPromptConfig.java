package com.pi.coding.prompt;

import com.pi.coding.resource.ContextFile;
import com.pi.coding.resource.Skill;

import java.util.List;
import java.util.Map;

/**
 * Configuration for building the system prompt.
 *
 * <p><b>Validates: Requirement 24.1</b>
 *
 * @param cwd                Working directory
 * @param skills             Pre-loaded skills
 * @param contextFiles       Pre-loaded context files (e.g., AGENTS.md)
 * @param customPrompt       Custom system prompt (replaces default, nullable)
 * @param appendSystemPrompt Text to append to system prompt (nullable)
 * @param selectedTools      Tools to include in prompt
 * @param toolSnippets       Optional one-line tool snippets keyed by tool name
 * @param promptGuidelines   Additional guideline bullets
 */
public record SystemPromptConfig(
        String cwd,
        List<Skill> skills,
        List<ContextFile> contextFiles,
        String customPrompt,
        String appendSystemPrompt,
        List<String> selectedTools,
        Map<String, String> toolSnippets,
        List<String> promptGuidelines
) {
    public SystemPromptConfig {
        if (cwd == null) cwd = System.getProperty("user.dir");
        if (skills == null) skills = List.of();
        if (contextFiles == null) contextFiles = List.of();
        if (selectedTools == null) selectedTools = List.of("read", "bash", "edit", "write");
        if (toolSnippets == null) toolSnippets = Map.of();
        if (promptGuidelines == null) promptGuidelines = List.of();
    }
}
