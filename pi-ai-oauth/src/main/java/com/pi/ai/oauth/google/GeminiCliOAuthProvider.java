package com.pi.ai.oauth.google;

import com.pi.ai.oauth.spi.OAuthCredentials;
import com.pi.ai.oauth.spi.OAuthLoginCallbacks;
import com.pi.ai.oauth.spi.OAuthProviderInterface;

import java.util.concurrent.CompletableFuture;

/**
 * Google Gemini CLI OAuth Provider（Google Cloud 认证）。
 *
 * <p>使用 Google Cloud OAuth 2.0 认证流程，
 * 获取 Cloud Code Assist API 的访问权限。
 */
public class GeminiCliOAuthProvider implements OAuthProviderInterface {

    public static final String ID = "google-gemini-cli";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Google Gemini CLI"; }

    @Override
    public boolean usesCallbackServer() { return true; }

    @Override
    public CompletableFuture<OAuthCredentials> login(OAuthLoginCallbacks callbacks) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Google Gemini CLI OAuth login requires browser interaction"));
    }

    @Override
    public CompletableFuture<OAuthCredentials> refreshToken(OAuthCredentials credentials) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Google Gemini CLI OAuth refresh not yet implemented"));
    }

    @Override
    public String getApiKey(OAuthCredentials credentials) {
        // Returns JSON-encoded { token, projectId }
        Object projectId = credentials.getExtra().get("projectId");
        return "{\"token\":\"" + credentials.getAccess() + "\",\"projectId\":\""
                + (projectId != null ? projectId : "") + "\"}";
    }
}
