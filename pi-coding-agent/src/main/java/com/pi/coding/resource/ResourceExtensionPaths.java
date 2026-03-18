package com.pi.coding.resource;

import java.util.List;

/**
 * Additional resource paths provided by extensions.
 */
public record ResourceExtensionPaths(
    List<String> extensionPaths,
    List<String> skillPaths,
    List<String> promptPaths
) {
    public ResourceExtensionPaths {
        if (extensionPaths == null) extensionPaths = List.of();
        if (skillPaths == null) skillPaths = List.of();
        if (promptPaths == null) promptPaths = List.of();
    }
}
