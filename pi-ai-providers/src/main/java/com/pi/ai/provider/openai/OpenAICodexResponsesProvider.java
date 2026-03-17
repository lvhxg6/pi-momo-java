package com.pi.ai.provider.openai;

import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.registry.ApiProvider;
import com.pi.ai.core.types.*;
import com.pi.ai.core.util.EnvApiKeys;

/**
 * OpenAI Codex Responses API Provider（openai-codex-responses）。
 *
 * <p>复用 {@link OpenAIResponsesProvider} 的核心逻辑，
 * 覆盖认证和端点配置。
 */
public class OpenAICodexResponsesProvider extends OpenAIResponsesProvider implements ApiProvider {

    private static final String API_ID = "openai-codex-responses";

    @Override
    public String api() {
        return API_ID;
    }

    @Override
    public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
        String apiKey = options != null ? options.getApiKey() : null;
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = EnvApiKeys.getEnvApiKey("openai-codex");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = EnvApiKeys.getEnvApiKey(model.provider());
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("No API key for provider: " + model.provider());
        }

        StreamOptions base = com.pi.ai.core.util.SimpleOptions.buildBaseOptions(model, options, apiKey);
        return stream(model, context, base);
    }
}
