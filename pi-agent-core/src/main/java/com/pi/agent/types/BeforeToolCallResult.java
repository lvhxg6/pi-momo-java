package com.pi.agent.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result returned by a {@link com.pi.agent.config.BeforeToolCallHook}.
 *
 * <p>When {@code block} is {@code true}, the agent loop prevents the tool from
 * executing and generates an error tool result containing {@code reason}
 * (or a default message if {@code reason} is {@code null}).
 *
 * @param block  if {@code true}, the tool call is blocked
 * @param reason human-readable explanation shown in the error tool result
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BeforeToolCallResult(
        Boolean block,
        String reason
) {
}
