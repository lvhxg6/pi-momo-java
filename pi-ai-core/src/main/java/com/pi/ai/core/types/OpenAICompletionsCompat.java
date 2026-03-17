package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenAI 兼容 Completions API 的兼容性配置。
 *
 * <p>用于覆盖基于 URL 的自动检测，为自定义 Provider 手动设置兼容性开关。
 * 所有字段均为可选（nullable），未设置时使用自动检测值。
 *
 * @param supportsStore                    是否支持 store 字段
 * @param supportsDeveloperRole            是否支持 developer 角色（vs system）
 * @param supportsReasoningEffort          是否支持 reasoning_effort 参数
 * @param reasoningEffortMap               思考级别到 Provider 特定 reasoning_effort 值的映射
 * @param supportsUsageInStreaming          是否支持流式响应中的 token 用量统计
 * @param maxTokensField                   最大 token 字段名（"max_completion_tokens" 或 "max_tokens"）
 * @param requiresToolResultName           工具结果是否需要 name 字段
 * @param requiresAssistantAfterToolResult 工具结果后是否需要插入 assistant 消息
 * @param requiresThinkingAsText           thinking 块是否需要转换为文本块
 * @param thinkingFormat                   思考参数格式（"openai"/"zai"/"qwen"/"qwen-chat-template"）
 * @param openRouterRouting                OpenRouter 路由偏好（可选）
 * @param vercelGatewayRouting             Vercel AI Gateway 路由偏好（可选）
 * @param supportsStrictMode               是否支持工具定义中的 strict 模式
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAICompletionsCompat(
    @JsonProperty("supportsStore") Boolean supportsStore,
    @JsonProperty("supportsDeveloperRole") Boolean supportsDeveloperRole,
    @JsonProperty("supportsReasoningEffort") Boolean supportsReasoningEffort,
    @JsonProperty("reasoningEffortMap") Map<ThinkingLevel, String> reasoningEffortMap,
    @JsonProperty("supportsUsageInStreaming") Boolean supportsUsageInStreaming,
    @JsonProperty("maxTokensField") String maxTokensField,
    @JsonProperty("requiresToolResultName") Boolean requiresToolResultName,
    @JsonProperty("requiresAssistantAfterToolResult") Boolean requiresAssistantAfterToolResult,
    @JsonProperty("requiresThinkingAsText") Boolean requiresThinkingAsText,
    @JsonProperty("thinkingFormat") String thinkingFormat,
    @JsonProperty("openRouterRouting") OpenRouterRouting openRouterRouting,
    @JsonProperty("vercelGatewayRouting") VercelGatewayRouting vercelGatewayRouting,
    @JsonProperty("supportsStrictMode") Boolean supportsStrictMode
) { }
