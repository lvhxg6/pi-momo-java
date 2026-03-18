package com.pi.coding.extension;

import java.util.List;
import java.util.Map;

/**
 * Loaded extension with all registered items.
 *
 * @param path             extension path
 * @param handlers         registered event handlers by event type
 * @param tools            registered tools by name
 * @param commands         registered commands by name
 * @param shortcuts        registered shortcuts by key
 * @param flags            registered flags by name
 * @param disposeHandler   dispose handler (may be null)
 */
public record Extension(
    String path,
    Map<String, List<ExtensionEventHandler<?>>> handlers,
    Map<String, RegisteredTool> tools,
    Map<String, RegisteredCommand> commands,
    Map<String, RegisteredShortcut> shortcuts,
    Map<String, RegisteredFlag> flags,
    Runnable disposeHandler
) { }
