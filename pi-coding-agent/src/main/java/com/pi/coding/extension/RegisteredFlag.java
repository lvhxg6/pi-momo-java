package com.pi.coding.extension;

/**
 * A flag registered by an extension.
 *
 * @param definition    the flag definition
 * @param extensionPath the path of the extension that registered this flag
 */
public record RegisteredFlag(
    FlagDefinition definition,
    String extensionPath
) { }
