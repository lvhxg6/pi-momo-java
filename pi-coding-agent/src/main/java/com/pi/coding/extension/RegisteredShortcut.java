package com.pi.coding.extension;

/**
 * A shortcut registered by an extension.
 *
 * @param definition    the shortcut definition
 * @param extensionPath the path of the extension that registered this shortcut
 */
public record RegisteredShortcut(
    ShortcutDefinition definition,
    String extensionPath
) { }
