package com.pi.agent.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for how tool calls from a single assistant message are executed.
 *
 * <ul>
 *   <li>{@link #SEQUENTIAL} — each tool call is prepared, executed, and finalized
 *       before the next one starts.</li>
 *   <li>{@link #PARALLEL} — tool calls are prepared sequentially, then executed
 *       concurrently. Final results are emitted in the original source order.</li>
 * </ul>
 */
public enum ToolExecutionMode {

    @JsonProperty("sequential")
    SEQUENTIAL,

    @JsonProperty("parallel")
    PARALLEL
}
