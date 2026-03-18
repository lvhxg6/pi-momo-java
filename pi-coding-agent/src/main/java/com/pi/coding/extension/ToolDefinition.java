package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.pi.agent.types.AgentToolResult;
import com.pi.agent.types.AgentToolUpdateCallback;
import com.pi.ai.core.types.CancellationSignal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Tool definition for registerTool().
 *
 * <p>Defines a tool that the LLM can call, including its name, description,
 * parameter schema, and execution function.
 *
 * @param name             tool name (used in LLM tool calls)
 * @param label            human-readable label for UI
 * @param description      description for LLM
 * @param promptSnippet    optional one-line snippet for the Available tools section
 * @param promptGuidelines optional guideline bullets for the Guidelines section
 * @param parameters       parameter schema (JSON Schema)
 * @param executor         the tool executor function
 * @param <TDetails>       the type of tool-specific details in the result
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolDefinition<TDetails>(
    @JsonProperty("name") String name,
    @JsonProperty("label") String label,
    @JsonProperty("description") String description,
    @JsonProperty("promptSnippet") String promptSnippet,
    @JsonProperty("promptGuidelines") List<String> promptGuidelines,
    @JsonProperty("parameters") JsonNode parameters,
    ToolExecutor<TDetails> executor
) {

    /**
     * Functional interface for tool execution.
     *
     * @param <TDetails> the type of tool-specific details in the result
     */
    @FunctionalInterface
    public interface ToolExecutor<TDetails> {
        /**
         * Execute the tool.
         *
         * @param toolCallId unique identifier for this tool call
         * @param params     validated tool parameters
         * @param signal     cancellation signal (may be null)
         * @param onUpdate   callback for streaming updates (may be null)
         * @param context    extension context
         * @return a future that completes with the tool result
         */
        CompletableFuture<AgentToolResult<TDetails>> execute(
            String toolCallId,
            JsonNode params,
            CancellationSignal signal,
            AgentToolUpdateCallback onUpdate,
            ExtensionContext context
        );
    }

    /**
     * Builder for creating ToolDefinition instances.
     *
     * @param <TDetails> the type of tool-specific details in the result
     */
    public static class Builder<TDetails> {
        private String name;
        private String label;
        private String description;
        private String promptSnippet;
        private List<String> promptGuidelines;
        private JsonNode parameters;
        private ToolExecutor<TDetails> executor;

        public Builder<TDetails> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<TDetails> label(String label) {
            this.label = label;
            return this;
        }

        public Builder<TDetails> description(String description) {
            this.description = description;
            return this;
        }

        public Builder<TDetails> promptSnippet(String promptSnippet) {
            this.promptSnippet = promptSnippet;
            return this;
        }

        public Builder<TDetails> promptGuidelines(List<String> promptGuidelines) {
            this.promptGuidelines = promptGuidelines;
            return this;
        }

        public Builder<TDetails> parameters(JsonNode parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder<TDetails> executor(ToolExecutor<TDetails> executor) {
            this.executor = executor;
            return this;
        }

        public ToolDefinition<TDetails> build() {
            return new ToolDefinition<>(name, label, description, promptSnippet, promptGuidelines, parameters, executor);
        }
    }

    public static <TDetails> Builder<TDetails> builder() {
        return new Builder<>();
    }
}
