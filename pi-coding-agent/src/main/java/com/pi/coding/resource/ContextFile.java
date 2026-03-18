package com.pi.coding.resource;

/**
 * A context file (e.g., AGENTS.md) loaded from the project.
 */
public record ContextFile(
    String path,
    String content
) {
    public ContextFile {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path cannot be null or empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
    }
}
