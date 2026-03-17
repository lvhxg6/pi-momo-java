package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Thinking/reasoning effort level for LLM calls.
 */
public enum ThinkingLevel {

    @JsonProperty("minimal")
    MINIMAL,

    @JsonProperty("low")
    LOW,

    @JsonProperty("medium")
    MEDIUM,

    @JsonProperty("high")
    HIGH,

    @JsonProperty("xhigh")
    XHIGH
}
