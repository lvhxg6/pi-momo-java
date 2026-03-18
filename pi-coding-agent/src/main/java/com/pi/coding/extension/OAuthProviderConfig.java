package com.pi.coding.extension;

import com.pi.ai.core.types.Model;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OAuth provider configuration for /login support.
 *
 * @param name          display name for the provider in login UI
 * @param loginHandler  handler to run the login flow
 * @param refreshHandler handler to refresh expired credentials
 * @param getApiKey     function to convert credentials to API key string
 * @param modifyModels  optional function to modify models based on credentials
 */
public record OAuthProviderConfig(
    String name,
    OAuthLoginHandler loginHandler,
    OAuthRefreshHandler refreshHandler,
    OAuthGetApiKeyHandler getApiKey,
    OAuthModifyModelsHandler modifyModels
) {

    /**
     * Handler for OAuth login flow.
     */
    @FunctionalInterface
    public interface OAuthLoginHandler {
        /**
         * Run the login flow.
         *
         * @param callbacks callbacks for the login flow
         * @return a future that completes with the credentials
         */
        CompletableFuture<OAuthCredentials> login(OAuthLoginCallbacks callbacks);
    }

    /**
     * Handler for refreshing OAuth credentials.
     */
    @FunctionalInterface
    public interface OAuthRefreshHandler {
        /**
         * Refresh expired credentials.
         *
         * @param credentials the current credentials
         * @return a future that completes with the updated credentials
         */
        CompletableFuture<OAuthCredentials> refresh(OAuthCredentials credentials);
    }

    /**
     * Handler for converting credentials to API key.
     */
    @FunctionalInterface
    public interface OAuthGetApiKeyHandler {
        /**
         * Convert credentials to API key string.
         *
         * @param credentials the credentials
         * @return the API key string
         */
        String getApiKey(OAuthCredentials credentials);
    }

    /**
     * Handler for modifying models based on credentials.
     */
    @FunctionalInterface
    public interface OAuthModifyModelsHandler {
        /**
         * Modify models for this provider.
         *
         * @param models      the models to modify
         * @param credentials the credentials
         * @return the modified models
         */
        List<Model> modifyModels(List<Model> models, OAuthCredentials credentials);
    }

    /**
     * Builder for creating OAuthProviderConfig instances.
     */
    public static class Builder {
        private String name;
        private OAuthLoginHandler loginHandler;
        private OAuthRefreshHandler refreshHandler;
        private OAuthGetApiKeyHandler getApiKey;
        private OAuthModifyModelsHandler modifyModels;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder loginHandler(OAuthLoginHandler loginHandler) {
            this.loginHandler = loginHandler;
            return this;
        }

        public Builder refreshHandler(OAuthRefreshHandler refreshHandler) {
            this.refreshHandler = refreshHandler;
            return this;
        }

        public Builder getApiKey(OAuthGetApiKeyHandler getApiKey) {
            this.getApiKey = getApiKey;
            return this;
        }

        public Builder modifyModels(OAuthModifyModelsHandler modifyModels) {
            this.modifyModels = modifyModels;
            return this;
        }

        public OAuthProviderConfig build() {
            return new OAuthProviderConfig(name, loginHandler, refreshHandler, getApiKey, modifyModels);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
