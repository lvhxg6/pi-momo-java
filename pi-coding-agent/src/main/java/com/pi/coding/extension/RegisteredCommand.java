package com.pi.coding.extension;

/**
 * A command registered by an extension.
 *
 * @param definition    the command definition
 * @param extensionPath the path of the extension that registered this command
 */
public record RegisteredCommand(
    CommandDefinition definition,
    String extensionPath
) { }
