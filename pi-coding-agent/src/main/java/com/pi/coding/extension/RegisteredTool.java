package com.pi.coding.extension;

/**
 * A tool registered by an extension.
 *
 * @param definition    the tool definition
 * @param extensionPath the path of the extension that registered this tool
 */
public record RegisteredTool(
    ToolDefinition<?> definition,
    String extensionPath
) { }
