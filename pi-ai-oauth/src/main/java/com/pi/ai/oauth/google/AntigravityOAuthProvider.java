package com.pi.ai.oauth.google;

import com.pi.ai.oauth.spi.OAuthCredentials;
import com.pi.ai.oauth.spi.OAuthLoginCallbacks;
import com.pi.ai.oauth.spi.OAuthProviderInterface;

import java.util.concurrent.CompletableFuture;

/**
 * Google Antigravity OAuth Provider。
 *
 * <p>使用 Google Cloud OAuth 2.0 认证流程，
 * 访问 Antigravity（Gemini 3、Claude、GPT-OSS via Google Cloud）。
 */
public class AntigravityOAuthProvider implements OAuthProviderInterface {

    public static final String ID = "google-antigravity";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Google Antigravity"; }

    @Override
    public boolean usesCallbackServer() { return true; }

    @Override
    public CompletableFuture<OAuthCredentials> login(OAuthLoginCallbacks callbacks) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Antigravity OAuth login requires browser interaction"));
    }

    @Override
    public CompletableFuture<OAuthCredentials> refreshToken(OAuthCredentials credentials) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Antigravity OAuth refresh not yet implemented"));
    }

    @Override
    public String getApiKey(OAuthCredentials credentials) {
        Object projectId = credentials.getExtra().get("projectId");
        return "{\"token\":\"" + credentials.getAccess() + "\",\"projectId\":\""
                + (projectId != null ? projectId : "") + "\"}";
    }
}
