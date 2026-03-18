package com.pi.coding.resource;

/**
 * Options for loading skills from a directory.
 */
public record LoadSkillsFromDirOptions(
    String dir,
    String source
) {
    public LoadSkillsFromDirOptions {
        if (dir == null || dir.isEmpty()) {
            throw new IllegalArgumentException("dir cannot be null or empty");
        }
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("source cannot be null or empty");
        }
    }
}
