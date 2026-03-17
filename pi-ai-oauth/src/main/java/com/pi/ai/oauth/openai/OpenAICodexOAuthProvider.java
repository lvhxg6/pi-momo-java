package com.pi.ai.oauth.openai;

import com.pi.ai.oauth.spi.OAuthCredentials;
import com.pi.ai.oauth.spi.OAuthLoginCallbacks;
import com.pi.ai.oauth.spi.OAuthProviderInterface;

import java.util.concurrent.CompletableFuture;

/**
 * OpenAI Codex OAuth Provider（ChatGPT Plus/Pro 订阅认证，PKCE 流程）。
 *
 * <p>使用 OpenAI 的 OAuth 2.0 + PKCE 认证流程，
 * 支持 ChatGPT Plus/Pro 订阅用户通过浏览器登录获取 Codex API 访问权限。
 */
public class OpenAICodexOAuthProvider implements OAuthProviderInterface {

    public static final String ID = "openai-codex";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "ChatGPT Plus/Pro (Codex Subscription)"; }

    @Override
    public boolean usesCallbackServer() { return true; }

    @Override
    public CompletableFuture<OAuthCredentials> login(OAuthLoginCallbacks callbacks) {
        // OpenAI Codex PKCE OAuth flow:
        // 1. Generate PKCE verifier/challenge
        // 2. Start local callback server
        // 3. Open browser to OpenAI OAuth URL with PKCE challenge
        // 4. Receive callback with authorization code
        // 5. Exchange code + verifier for tokens
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("OpenAI Codex OAuth login requires browser interaction"));
    }

    @Override
    public CompletableFuture<OAuthCredentials> refreshToken(OAuthCredentials credentials) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("OpenAI Codex OAuth refresh not yet implemented"));
    }

    @Override
    public String getApiKey(OAuthCredentials credentials) {
        return credentials.getAccess();
    }
}
