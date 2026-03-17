package com.pi.ai.provider.openai;

import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.registry.ApiProvider;
import com.pi.ai.core.types.*;
import com.pi.ai.core.util.EnvApiKeys;

/**
 * Azure OpenAI Responses API Provider（azure-openai-responses）。
 *
 * <p>复用 {@link OpenAIResponsesProvider} 的核心逻辑，
 * 覆盖认证方式（api-key 头 + api-version 参数）和 URL 构建。
 */
public class AzureOpenAIResponsesProvider extends OpenAIResponsesProvider implements ApiProvider {

    private static final String API_ID = "azure-openai-responses";

    @Override
    public String api() {
        return API_ID;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        // 确保使用 Azure 认证方式
        String apiKey = options != null ? options.getApiKey() : null;
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = EnvApiKeys.getEnvApiKey("azure-openai");
        }

        StreamOptions adapted = StreamOptions.builder()
                .temperature(options != null ? options.getTemperature() : null)
                .maxTokens(options != null ? options.getMaxTokens() : null)
                .apiKey(apiKey)
                .cacheRetention(options != null ? options.getCacheRetention() : null)
                .sessionId(options != null ? options.getSessionId() : null)
                .headers(buildAzureHeaders(model, options, apiKey))
                .transport(options != null ? options.getTransport() : null)
                .maxRetryDelayMs(options != null ? options.getMaxRetryDelayMs() : null)
                .signal(options != null ? options.getSignal() : null)
                .metadata(options != null ? options.getMetadata() : null)
                .build();

        return super.stream(model, context, adapted);
    }

    @Override
    public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
        String apiKey = options != null ? options.getApiKey() : null;
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = EnvApiKeys.getEnvApiKey("azure-openai");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("No API key for provider: azure-openai");
        }

        StreamOptions base = com.pi.ai.core.util.SimpleOptions.buildBaseOptions(model, options, apiKey);
        return stream(model, context, base);
    }

    private java.util.Map<String, String> buildAzureHeaders(Model model, StreamOptions options, String apiKey) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        // Azure 使用 api-key 头而非 Bearer token
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.put("api-key", apiKey);
        }
        if (model.headers() != null) headers.putAll(model.headers());
        if (options != null && options.getHeaders() != null) headers.putAll(options.getHeaders());
        return headers;
    }
}
