package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAI Responses API 的兼容性配置。
 *
 * <p>当前为空，预留给未来扩展。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAIResponsesCompat() { }
