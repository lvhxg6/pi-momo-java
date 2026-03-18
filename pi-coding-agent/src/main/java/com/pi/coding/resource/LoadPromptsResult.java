package com.pi.coding.resource;

import java.util.List;

/**
 * Result of loading prompt templates.
 */
public record LoadPromptsResult(
    List<PromptTemplate> prompts,
    List<ResourceDiagnostic> diagnostics
) {
    public LoadPromptsResult {
        if (prompts == null) {
            throw new IllegalArgumentException("prompts cannot be null");
        }
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics cannot be null");
        }
    }
}
