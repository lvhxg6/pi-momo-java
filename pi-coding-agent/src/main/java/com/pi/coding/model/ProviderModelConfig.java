package com.pi.coding.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.ai.core.types.ModelCost;

/**
 * Model configuration for a dynamically registered provider.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderModelConfig(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("reasoning") Boolean reasoning,
    @JsonProperty("input") String input,
    @JsonProperty("cost") ModelCost cost,
    @JsonProperty("contextWindow") Integer contextWindow,
    @JsonProperty("maxTokens") Integer maxTokens
) {}
