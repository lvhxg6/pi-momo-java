package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Configuration for registering a provider via registerProvider().
 *
 * @param baseUrl    base URL for the API endpoint
 * @param apiKey     API key or environment variable name
 * @param api        API type (e.g., "anthropic-messages", "openai-completions")
 * @param headers    custom headers to include in requests
 * @param authHeader if true, adds Authorization: Bearer header with the resolved API key
 * @param models     models to register (replaces all existing models for this provider)
 * @param oauth      OAuth provider configuration (may be null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderConfig(
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("apiKey") String apiKey,
    @JsonProperty("api") String api,
    @JsonProperty("headers") Map<String, String> headers,
    @JsonProperty("authHeader") Boolean authHeader,
    @JsonProperty("models") List<ProviderModelConfig> models,
    OAuthProviderConfig oauth
) {

    /**
     * Builder for creating ProviderConfig instances.
     */
    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private String api;
        private Map<String, String> headers;
        private Boolean authHeader;
        private List<ProviderModelConfig> models;
        private OAuthProviderConfig oauth;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder api(String api) {
            this.api = api;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder authHeader(Boolean authHeader) {
            this.authHeader = authHeader;
            return this;
        }

        public Builder models(List<ProviderModelConfig> models) {
            this.models = models;
            return this;
        }

        public Builder oauth(OAuthProviderConfig oauth) {
            this.oauth = oauth;
            return this;
        }

        public ProviderConfig build() {
            return new ProviderConfig(baseUrl, apiKey, api, headers, authHeader, models, oauth);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
