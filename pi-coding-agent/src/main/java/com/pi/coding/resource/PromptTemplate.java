package com.pi.coding.resource;

/**
 * Represents a prompt template loaded from a markdown file.
 */
public record PromptTemplate(
    String name,
    String description,
    String content,
    String source,
    String filePath
) {
    public PromptTemplate {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (description == null) {
            throw new IllegalArgumentException("description cannot be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("source cannot be null or empty");
        }
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath cannot be null or empty");
        }
    }
}
