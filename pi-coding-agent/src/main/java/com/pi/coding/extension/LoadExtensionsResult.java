package com.pi.coding.extension;

import java.util.List;

/**
 * Result of loading extensions.
 *
 * @param extensions loaded extensions
 * @param errors     errors that occurred during loading
 */
public record LoadExtensionsResult(
    List<Extension> extensions,
    List<LoadError> errors
) {

    /**
     * An error that occurred while loading an extension.
     *
     * @param path  the extension path or factory class name
     * @param error the error message
     */
    public record LoadError(
        String path,
        String error
    ) { }
}
