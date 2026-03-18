package com.pi.coding.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RPC event sent via stdout as JSON lines.
 * Wraps agent session events for the RPC consumer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcEvent(
        @JsonProperty("type") String type,
        @JsonProperty("event") String event,
        @JsonProperty("data") Object data
) {
    public static RpcEvent of(String eventType, Object data) {
        return new RpcEvent("event", eventType, data);
    }
}
