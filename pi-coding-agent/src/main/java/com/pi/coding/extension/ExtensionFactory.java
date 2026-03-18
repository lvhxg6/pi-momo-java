package com.pi.coding.extension;

/**
 * Extension factory function type.
 *
 * <p>Extensions are created by factory functions that receive an ExtensionAPI
 * and use it to register tools, commands, shortcuts, flags, and event handlers.
 */
@FunctionalInterface
public interface ExtensionFactory {

    /**
     * Create the extension.
     *
     * @param api the extension API
     */
    void create(ExtensionAPI api);
}
