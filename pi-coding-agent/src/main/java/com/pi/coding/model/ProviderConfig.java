package com.pi.coding.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a dynamically registered provider (from extensions).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderConfig(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("headers") Map<String, String> headers,
    @JsonProperty("apiKey") String apiKey,
    @JsonProperty("models") List<ProviderModelConfig> models
) {}
