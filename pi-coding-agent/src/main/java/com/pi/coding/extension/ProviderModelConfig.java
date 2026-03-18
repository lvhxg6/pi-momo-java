package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.ai.core.types.ModelCost;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a model within a provider.
 *
 * @param id            model ID (e.g., "claude-sonnet-4-20250514")
 * @param name          display name (e.g., "Claude 4 Sonnet")
 * @param api           API type override for this model
 * @param reasoning     whether the model supports extended thinking
 * @param input         supported input types (e.g., ["text", "image"])
 * @param cost          cost per token
 * @param contextWindow maximum context window size in tokens
 * @param maxTokens     maximum output tokens
 * @param headers       custom headers for this model
 * @param compat        OpenAI compatibility settings
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderModelConfig(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("api") String api,
    @JsonProperty("reasoning") boolean reasoning,
    @JsonProperty("input") List<String> input,
    @JsonProperty("cost") ModelCost cost,
    @JsonProperty("contextWindow") int contextWindow,
    @JsonProperty("maxTokens") int maxTokens,
    @JsonProperty("headers") Map<String, String> headers,
    @JsonProperty("compat") Object compat
) {

    /**
     * Builder for creating ProviderModelConfig instances.
     */
    public static class Builder {
        private String id;
        private String name;
        private String api;
        private boolean reasoning;
        private List<String> input;
        private ModelCost cost;
        private int contextWindow;
        private int maxTokens;
        private Map<String, String> headers;
        private Object compat;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder api(String api) {
            this.api = api;
            return this;
        }

        public Builder reasoning(boolean reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder input(List<String> input) {
            this.input = input;
            return this;
        }

        public Builder cost(ModelCost cost) {
            this.cost = cost;
            return this;
        }

        public Builder contextWindow(int contextWindow) {
            this.contextWindow = contextWindow;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder compat(Object compat) {
            this.compat = compat;
            return this;
        }

        public ProviderModelConfig build() {
            return new ProviderModelConfig(id, name, api, reasoning, input, cost, contextWindow, maxTokens, headers, compat);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
