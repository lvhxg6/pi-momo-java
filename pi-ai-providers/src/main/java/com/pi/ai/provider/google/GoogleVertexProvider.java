package com.pi.ai.provider.google;

import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.registry.ApiProvider;
import com.pi.ai.core.types.*;
import com.pi.ai.core.util.EnvApiKeys;
import com.pi.ai.core.util.SimpleOptions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Google Vertex AI Provider（google-vertex）。
 *
 * <p>复用 {@link GoogleGeminiProvider} 的核心 SSE 处理逻辑，
 * 覆盖认证方式（ADC 或 API Key）和 URL 构建。
 */
public class GoogleVertexProvider extends GoogleGeminiProvider implements ApiProvider {

    private static final String API_ID = "google-vertex";
    private static final String API_VERSION = "v1";

    @Override
    public String api() {
        return API_ID;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        // Vertex AI 使用不同的 URL 格式
        // 如果有 apiKey，使用 API Key 认证；否则需要 ADC（project + location）
        String apiKey = resolveApiKey(model, options);

        if (apiKey != null && !apiKey.isEmpty()) {
            // API Key 模式 - 使用 Vertex AI 的 API Key 端点
            return super.stream(model, context, options);
        }

        // ADC 模式 - 需要 project 和 location
        String project = resolveProject(options);
        String location = resolveLocation(options);
        if (project == null || location == null) {
            throw new IllegalStateException(
                    "Vertex AI requires project and location. Set GOOGLE_CLOUD_PROJECT and GOOGLE_CLOUD_LOCATION.");
        }

        // 构建 Vertex AI URL
        StreamOptions adapted = StreamOptions.builder()
                .temperature(options != null ? options.getTemperature() : null)
                .maxTokens(options != null ? options.getMaxTokens() : null)
                .apiKey(apiKey)
                .cacheRetention(options != null ? options.getCacheRetention() : null)
                .sessionId(options != null ? options.getSessionId() : null)
                .headers(options != null ? options.getHeaders() : null)
                .transport(options != null ? options.getTransport() : null)
                .maxRetryDelayMs(options != null ? options.getMaxRetryDelayMs() : null)
                .signal(options != null ? options.getSignal() : null)
                .metadata(buildVertexMeta(options, project, location))
                .build();

        return super.stream(model, context, adapted);
    }

    @Override
    public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
        StreamOptions base = SimpleOptions.buildBaseOptions(model, options, null);
        ThinkingLevel reasoning = options != null ? options.getReasoning() : null;

        if (reasoning == null) {
            return streamWithVertexThinking(model, context, base, false, null, null);
        }

        ThinkingLevel clamped = SimpleOptions.clampReasoning(reasoning);
        if (clamped == null) clamped = reasoning;

        if (GoogleShared.isGemini3ProModel(model.id()) || GoogleShared.isGemini3FlashModel(model.id())) {
            GoogleShared.GoogleThinkingLevel level = GoogleShared.getGemini3ThinkingLevel(clamped, model.id());
            return streamWithVertexThinking(model, context, base, true, level, null);
        }

        ThinkingBudgets customBudgets = options != null ? options.getThinkingBudgets() : null;
        int budget = GoogleShared.getGoogleBudget(model.id(), clamped, customBudgets);
        return streamWithVertexThinking(model, context, base, true, null, budget);
    }

    private AssistantMessageEventStream streamWithVertexThinking(Model model, Context context,
                                                                  StreamOptions base, boolean enabled,
                                                                  GoogleShared.GoogleThinkingLevel level,
                                                                  Integer budget) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (base.getMetadata() != null) meta.putAll(base.getMetadata());
        meta.put("_thinking_enabled", enabled);
        if (level != null) meta.put("_thinking_level", level.name());
        if (budget != null) meta.put("_thinking_budget", budget);

        StreamOptions adapted = StreamOptions.builder()
                .temperature(base.getTemperature())
                .maxTokens(base.getMaxTokens())
                .apiKey(base.getApiKey())
                .cacheRetention(base.getCacheRetention())
                .sessionId(base.getSessionId())
                .headers(base.getHeaders())
                .transport(base.getTransport())
                .maxRetryDelayMs(base.getMaxRetryDelayMs())
                .signal(base.getSignal())
                .metadata(meta)
                .build();
        return stream(model, context, adapted);
    }

    private Map<String, Object> buildVertexMeta(StreamOptions options, String project, String location) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (options != null && options.getMetadata() != null) meta.putAll(options.getMetadata());
        meta.put("_vertex_project", project);
        meta.put("_vertex_location", location);
        return meta;
    }

    private String resolveProject(StreamOptions options) {
        if (options != null && options.getMetadata() != null) {
            Object p = options.getMetadata().get("project");
            if (p instanceof String s && !s.isEmpty()) return s;
        }
        String env = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (env != null && !env.isEmpty()) return env;
        env = System.getenv("GCLOUD_PROJECT");
        return env;
    }

    private String resolveLocation(StreamOptions options) {
        if (options != null && options.getMetadata() != null) {
            Object l = options.getMetadata().get("location");
            if (l instanceof String s && !s.isEmpty()) return s;
        }
        return System.getenv("GOOGLE_CLOUD_LOCATION");
    }
}
