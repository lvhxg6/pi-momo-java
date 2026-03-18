package com.pi.coding.tool;

/**
 * Details returned by the edit tool execution.
 *
 * @param path The file path that was edited
 * @param firstChangedLine Line number of the first change (for editor navigation)
 * @param diff Unified diff of the changes made
 */
public record EditToolDetails(
    String path,
    int firstChangedLine,
    String diff
) {}
