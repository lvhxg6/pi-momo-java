package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Transport protocol for streaming connections.
 */
public enum Transport {

    @JsonProperty("sse")
    SSE,

    @JsonProperty("websocket")
    WEBSOCKET,

    @JsonProperty("auto")
    AUTO
}
