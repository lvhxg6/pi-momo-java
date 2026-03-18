package com.pi.coding.resource;

/**
 * Information about a resource name collision.
 */
public record ResourceCollision(
    String resourceType,
    String name,
    String winnerPath,
    String loserPath
) {
    public ResourceCollision {
        if (resourceType == null || resourceType.isEmpty()) {
            throw new IllegalArgumentException("resourceType cannot be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (winnerPath == null || winnerPath.isEmpty()) {
            throw new IllegalArgumentException("winnerPath cannot be null or empty");
        }
        if (loserPath == null || loserPath.isEmpty()) {
            throw new IllegalArgumentException("loserPath cannot be null or empty");
        }
    }
}
