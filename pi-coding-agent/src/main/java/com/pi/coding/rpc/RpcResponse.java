package com.pi.coding.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RPC responses sent via stdout as JSON lines.
 *
 * <p><b>Validates: Requirements 20.2, 20.17</b>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcResponse(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("command") String command,
        @JsonProperty("success") boolean success,
        @JsonProperty("data") Object data,
        @JsonProperty("error") String error
) {
    /** Create a success response with data. */
    public static RpcResponse success(String id, String command, Object data) {
        return new RpcResponse(id, "response", command, true, data, null);
    }

    /** Create a success response without data. */
    public static RpcResponse success(String id, String command) {
        return new RpcResponse(id, "response", command, true, null, null);
    }

    /** Create an error response. */
    public static RpcResponse error(String id, String command, String error) {
        return new RpcResponse(id, "response", command, false, null, error);
    }
}
