package com.pi.ai.oauth.anthropic;

import com.pi.ai.oauth.spi.OAuthCredentials;
import com.pi.ai.oauth.spi.OAuthLoginCallbacks;
import com.pi.ai.oauth.spi.OAuthProviderInterface;

import java.util.concurrent.CompletableFuture;

/**
 * Anthropic OAuth Provider（Claude Pro/Max 订阅认证）。
 *
 * <p>使用 Anthropic 的 OAuth 2.0 认证流程，
 * 支持 Claude Pro/Max 订阅用户通过浏览器登录获取 API 访问权限。
 */
public class AnthropicOAuthProvider implements OAuthProviderInterface {

    public static final String ID = "anthropic";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Anthropic (Claude Pro/Max)"; }

    @Override
    public boolean usesCallbackServer() { return true; }

    @Override
    public CompletableFuture<OAuthCredentials> login(OAuthLoginCallbacks callbacks) {
        // Anthropic OAuth login flow:
        // 1. Start local callback server
        // 2. Open browser to Anthropic OAuth URL
        // 3. Receive callback with authorization code
        // 4. Exchange code for tokens
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Anthropic OAuth login requires browser interaction"));
    }

    @Override
    public CompletableFuture<OAuthCredentials> refreshToken(OAuthCredentials credentials) {
        // Refresh using Anthropic's token endpoint
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Anthropic OAuth refresh not yet implemented"));
    }

    @Override
    public String getApiKey(OAuthCredentials credentials) {
        return credentials.getAccess();
    }
}
