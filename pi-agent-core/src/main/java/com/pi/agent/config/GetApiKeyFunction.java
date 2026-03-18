package com.pi.agent.config;

import java.util.concurrent.CompletableFuture;

/**
 * Optional callback that dynamically resolves the API key for each LLM call.
 *
 * <p>When configured, the agent loop invokes this function before every
 * streaming call, passing the provider identifier (e.g. {@code "anthropic"},
 * {@code "openai"}). The resolved key overrides any statically configured
 * API key in the stream options.
 *
 * <p><b>Validates: Requirements 13.7</b>
 */
@FunctionalInterface
public interface GetApiKeyFunction {

    /**
     * Resolves the API key for the given provider.
     *
     * @param provider the LLM provider identifier
     * @return a future resolving to the API key string
     */
    CompletableFuture<String> getApiKey(String provider);
}
