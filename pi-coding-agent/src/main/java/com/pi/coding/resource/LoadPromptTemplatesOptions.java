package com.pi.coding.resource;

import java.util.List;

/**
 * Options for loading prompt templates.
 */
public record LoadPromptTemplatesOptions(
    String cwd,
    String agentDir,
    List<String> promptPaths,
    boolean includeDefaults
) {
    public LoadPromptTemplatesOptions {
        if (cwd == null || cwd.isEmpty()) {
            throw new IllegalArgumentException("cwd cannot be null or empty");
        }
        if (agentDir == null || agentDir.isEmpty()) {
            throw new IllegalArgumentException("agentDir cannot be null or empty");
        }
        if (promptPaths == null) {
            throw new IllegalArgumentException("promptPaths cannot be null");
        }
    }
    
    /**
     * Create options with defaults.
     */
    public LoadPromptTemplatesOptions(String cwd, String agentDir) {
        this(cwd, agentDir, List.of(), true);
    }
}
