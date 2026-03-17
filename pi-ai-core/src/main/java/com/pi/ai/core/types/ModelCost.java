package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 模型定价信息，单位为 $/百万 token。
 *
 * @param input      输入 token 单价（$/百万 token）
 * @param output     输出 token 单价（$/百万 token）
 * @param cacheRead  缓存读取 token 单价（$/百万 token）
 * @param cacheWrite 缓存写入 token 单价（$/百万 token）
 */
public record ModelCost(
    @JsonProperty("input") double input,
    @JsonProperty("output") double output,
    @JsonProperty("cacheRead") double cacheRead,
    @JsonProperty("cacheWrite") double cacheWrite
) { }
