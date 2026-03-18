package com.pi.coding.resource;

import java.util.List;

/**
 * Options for loading skills from all configured locations.
 */
public record LoadSkillsOptions(
    String cwd,
    String agentDir,
    List<String> skillPaths,
    boolean includeDefaults
) {
    public LoadSkillsOptions {
        if (cwd == null || cwd.isEmpty()) {
            throw new IllegalArgumentException("cwd cannot be null or empty");
        }
        if (agentDir == null || agentDir.isEmpty()) {
            throw new IllegalArgumentException("agentDir cannot be null or empty");
        }
        if (skillPaths == null) {
            throw new IllegalArgumentException("skillPaths cannot be null");
        }
    }
    
    /**
     * Create options with defaults.
     */
    public LoadSkillsOptions(String cwd, String agentDir) {
        this(cwd, agentDir, List.of(), true);
    }
}
