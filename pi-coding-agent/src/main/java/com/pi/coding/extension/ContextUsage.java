package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Context usage information for the active model.
 *
 * @param tokens        estimated context tokens, or null if unknown
 * @param contextWindow context window size in tokens
 * @param percent       context usage as percentage of context window, or null if tokens is unknown
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextUsage(
    @JsonProperty("tokens") Integer tokens,
    @JsonProperty("contextWindow") int contextWindow,
    @JsonProperty("percent") Double percent
) { }
