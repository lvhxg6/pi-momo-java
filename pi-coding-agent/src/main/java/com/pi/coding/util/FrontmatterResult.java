package com.pi.coding.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Result of parsing frontmatter from a markdown file.
 * 
 * @param data Parsed frontmatter data as a map
 * @param content Body content after frontmatter
 */
public record FrontmatterResult(
    Map<String, Object> data,
    String content
) {
    @JsonCreator
    public FrontmatterResult(
        @JsonProperty("data") Map<String, Object> data,
        @JsonProperty("content") String content
    ) {
        this.data = data != null ? Map.copyOf(data) : Map.of();
        this.content = content != null ? content : "";
    }
    
    /**
     * Get a frontmatter value as a String.
     * Returns null if the key doesn't exist.
     */
    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Get a frontmatter value as a Boolean.
     * Returns null if the key doesn't exist or cannot be converted.
     */
    public Boolean getBoolean(String key) {
        Object value = data.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }
}
