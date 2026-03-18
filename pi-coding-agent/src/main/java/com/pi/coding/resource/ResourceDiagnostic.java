package com.pi.coding.resource;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Diagnostic message from resource loading.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceDiagnostic(
    String type,
    String message,
    String path,
    ResourceCollision collision
) {
    public ResourceDiagnostic(String type, String message, String path) {
        this(type, message, path, null);
    }
    
    public ResourceDiagnostic {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type cannot be null or empty");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message cannot be null or empty");
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path cannot be null or empty");
        }
    }
}
