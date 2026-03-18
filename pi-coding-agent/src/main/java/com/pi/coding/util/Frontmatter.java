package com.pi.coding.util;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;

/**
 * Utility for parsing YAML frontmatter from markdown files.
 * 
 * <p>Frontmatter is a YAML block at the beginning of a file, delimited by "---":
 * <pre>
 * ---
 * key: value
 * another: data
 * ---
 * 
 * Content here...
 * </pre>
 */
public final class Frontmatter {
    
    private Frontmatter() {
        // Utility class
    }
    
    /**
     * Parse frontmatter from content.
     * 
     * @param content The content to parse
     * @return Parsed frontmatter and body content
     */
    public static FrontmatterResult parseFrontmatter(String content) {
        if (content == null || content.isEmpty()) {
            return new FrontmatterResult(Map.of(), "");
        }
        
        String normalized = normalizeNewlines(content);
        
        if (!normalized.startsWith("---")) {
            return new FrontmatterResult(Map.of(), content);
        }
        
        // Find the end of frontmatter block (looking for "\n---" after position 3)
        int endIndex = normalized.indexOf("\n---", 3);
        if (endIndex == -1) {
            // No closing delimiter found - return original content
            return new FrontmatterResult(Map.of(), content);
        }
        
        // Extract YAML string (between "---\n" and "\n---")
        // Start from index 4 (after "---\n"), end at endIndex (before "\n---")
        String yamlString;
        if (endIndex > 4) {
            yamlString = normalized.substring(4, endIndex);
        } else {
            // Empty frontmatter case: "---\n---"
            yamlString = "";
        }
        
        // Extract body (after "\n---\n" or "\n---")
        String body;
        int bodyStart = endIndex + 4; // Skip "\n---"
        if (bodyStart < normalized.length()) {
            // Check if there's a newline after the closing ---
            if (bodyStart < normalized.length() && normalized.charAt(bodyStart) == '\n') {
                bodyStart++; // Skip the newline
            }
            body = bodyStart < normalized.length() ? normalized.substring(bodyStart) : "";
        } else {
            body = "";
        }
        
        // Parse YAML
        Map<String, Object> data;
        try {
            if (yamlString.isEmpty()) {
                data = Map.of();
            } else {
                Yaml yaml = new Yaml();
                Object parsed = yaml.load(yamlString);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) parsed;
                    data = map;
                } else {
                    data = Map.of();
                }
            }
        } catch (Exception e) {
            // If YAML parsing fails, return empty frontmatter
            data = Map.of();
        }
        
        return new FrontmatterResult(data, body);
    }
    
    /**
     * Strip frontmatter from content, returning only the body.
     * 
     * @param content The content to strip
     * @return Body content without frontmatter
     */
    public static String stripFrontmatter(String content) {
        return parseFrontmatter(content).content();
    }
    
    /**
     * Normalize line endings to \n.
     */
    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }
}
