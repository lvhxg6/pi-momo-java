package com.pi.coding.resource;

import java.util.List;

/**
 * Result of loading skills from directories.
 */
public record LoadSkillsResult(
    List<Skill> skills,
    List<ResourceDiagnostic> diagnostics
) {
    public LoadSkillsResult {
        if (skills == null) {
            throw new IllegalArgumentException("skills cannot be null");
        }
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics cannot be null");
        }
    }
}
