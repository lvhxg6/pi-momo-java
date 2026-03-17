package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * LLM 模型定义，包含模型元数据、定价、能力和兼容性配置。
 *
 * <p>Java 中没有条件类型，{@code compat} 字段使用 {@link OpenAICompletionsCompat} 类型。
 * 对于 OpenAI Responses API 模型，compat 为 null（{@link OpenAIResponsesCompat} 当前为空）。
 *
 * @param id            模型唯一标识（如 "claude-sonnet-4-20250514"）
 * @param name          模型显示名称（如 "Claude Sonnet 4"）
 * @param api           API 协议标识（如 "anthropic-messages"、"openai-completions"）
 * @param provider      服务提供商标识（如 "anthropic"、"openai"）
 * @param baseUrl       API 基础 URL
 * @param reasoning     是否支持推理/思考功能
 * @param input         支持的输入类型列表（如 ["text", "image"]）
 * @param cost          模型定价信息
 * @param contextWindow 上下文窗口大小（token 数）
 * @param maxTokens     最大输出 token 数
 * @param headers       自定义 HTTP 头（可选）
 * @param compat        兼容性配置（可选，仅 openai-completions API 使用）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Model(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("api") String api,
    @JsonProperty("provider") String provider,
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("reasoning") boolean reasoning,
    @JsonProperty("input") List<String> input,
    @JsonProperty("cost") ModelCost cost,
    @JsonProperty("contextWindow") int contextWindow,
    @JsonProperty("maxTokens") int maxTokens,
    @JsonProperty("headers") Map<String, String> headers,
    @JsonProperty("compat") OpenAICompletionsCompat compat
) { }
