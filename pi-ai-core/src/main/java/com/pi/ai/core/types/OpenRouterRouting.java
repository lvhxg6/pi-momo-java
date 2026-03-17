package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenRouter 路由偏好配置。
 *
 * <p>控制 OpenRouter 将请求路由到哪些上游 Provider。
 *
 * @param only  仅使用的 Provider slug 列表（可选）
 * @param order 按优先级排序的 Provider slug 列表（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenRouterRouting(
    @JsonProperty("only") List<String> only,
    @JsonProperty("order") List<String> order
) { }
