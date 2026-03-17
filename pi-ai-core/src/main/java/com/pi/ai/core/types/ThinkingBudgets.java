package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 每个思考级别对应的 token 预算配置。
 *
 * <p>所有字段均为可选（nullable），未设置时使用默认预算值。
 *
 * @param minimal minimal 级别的 token 预算
 * @param low     low 级别的 token 预算
 * @param medium  medium 级别的 token 预算
 * @param high    high 级别的 token 预算
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThinkingBudgets(
    @JsonProperty("minimal") Integer minimal,
    @JsonProperty("low") Integer low,
    @JsonProperty("medium") Integer medium,
    @JsonProperty("high") Integer high
) { }
