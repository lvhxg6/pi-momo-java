package com.pi.coding.resource;

/**
 * Represents a skill loaded from a SKILL.md file.
 * 
 * <p>Skills provide specialized instructions for specific tasks.
 * They are discovered from configured directories and can be invoked
 * by the agent when the task matches the skill's description.
 */
public record Skill(
    String name,
    String description,
    String filePath,
    String baseDir,
    String source,
    boolean disableModelInvocation
) {
    /**
     * Create a skill.
     * 
     * @param name Skill name (must match parent directory)
     * @param description Skill description
     * @param filePath Path to SKILL.md file
     * @param baseDir Parent directory of SKILL.md
     * @param source Source identifier ("user", "project", or "path")
     * @param disableModelInvocation Whether to exclude from model prompt
     */
    public Skill {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (description == null || description.isEmpty()) {
            throw new IllegalArgumentException("description cannot be null or empty");
        }
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath cannot be null or empty");
        }
        if (baseDir == null || baseDir.isEmpty()) {
            throw new IllegalArgumentException("baseDir cannot be null or empty");
        }
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("source cannot be null or empty");
        }
    }
}
