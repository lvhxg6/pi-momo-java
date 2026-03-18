package com.pi.agent.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pi.ai.core.types.UserContentBlock;

import java.util.List;

/**
 * Result returned by an {@link com.pi.agent.config.AfterToolCallHook}.
 *
 * <p>Each non-{@code null} field overrides the corresponding field in the
 * original tool execution result. Fields left {@code null} preserve the
 * original values (field-level merge semantics).
 *
 * @param content replacement content blocks (text / image), or {@code null} to keep original
 * @param details replacement details payload, or {@code null} to keep original
 * @param isError replacement error flag, or {@code null} to keep original
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AfterToolCallResult(
        List<UserContentBlock> content,
        Object details,
        Boolean isError
) {
}
