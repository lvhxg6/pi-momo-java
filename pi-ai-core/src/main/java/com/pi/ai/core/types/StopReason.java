package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reason why the LLM stopped generating.
 */
public enum StopReason {

    @JsonProperty("stop")
    STOP,

    @JsonProperty("length")
    LENGTH,

    @JsonProperty("toolUse")
    TOOL_USE,

    @JsonProperty("error")
    ERROR,

    @JsonProperty("aborted")
    ABORTED
}
