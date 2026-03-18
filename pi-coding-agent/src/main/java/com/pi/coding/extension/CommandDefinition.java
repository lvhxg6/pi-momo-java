package com.pi.coding.extension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Command definition for registerCommand().
 *
 * <p>Defines a slash command that users can invoke.
 *
 * @param name                    command name (without leading slash)
 * @param description             command description
 * @param getArgumentCompletions  function to provide argument completions (may be null)
 * @param handler                 command handler
 */
public record CommandDefinition(
    String name,
    String description,
    Function<String, List<AutocompleteItem>> getArgumentCompletions,
    CommandHandler handler
) {

    /**
     * Functional interface for command execution.
     */
    @FunctionalInterface
    public interface CommandHandler {
        /**
         * Handle the command.
         *
         * @param args    command arguments
         * @param context extension command context
         * @return a future that completes when the command is done
         */
        CompletableFuture<Void> handle(String args, ExtensionCommandContext context);
    }

    /**
     * Builder for creating CommandDefinition instances.
     */
    public static class Builder {
        private String name;
        private String description;
        private Function<String, List<AutocompleteItem>> getArgumentCompletions;
        private CommandHandler handler;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder getArgumentCompletions(Function<String, List<AutocompleteItem>> getArgumentCompletions) {
            this.getArgumentCompletions = getArgumentCompletions;
            return this;
        }

        public Builder handler(CommandHandler handler) {
            this.handler = handler;
            return this;
        }

        public CommandDefinition build() {
            return new CommandDefinition(name, description, getArgumentCompletions, handler);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
