package com.pi.coding.extension;

import java.util.concurrent.CompletableFuture;

/**
 * Shortcut definition for registerShortcut().
 *
 * <p>Defines a keyboard shortcut that users can press.
 *
 * @param key         the key identifier (e.g., "ctrl+k", "f1")
 * @param description shortcut description (may be null)
 * @param handler     shortcut handler
 */
public record ShortcutDefinition(
    String key,
    String description,
    ShortcutHandler handler
) {

    /**
     * Functional interface for shortcut execution.
     */
    @FunctionalInterface
    public interface ShortcutHandler {
        /**
         * Handle the shortcut.
         *
         * @param context extension context
         * @return a future that completes when the shortcut is handled
         */
        CompletableFuture<Void> handle(ExtensionContext context);
    }

    /**
     * Builder for creating ShortcutDefinition instances.
     */
    public static class Builder {
        private String key;
        private String description;
        private ShortcutHandler handler;

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder handler(ShortcutHandler handler) {
            this.handler = handler;
            return this;
        }

        public ShortcutDefinition build() {
            return new ShortcutDefinition(key, description, handler);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
