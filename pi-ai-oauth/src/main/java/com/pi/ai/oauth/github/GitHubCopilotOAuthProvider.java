package com.pi.ai.oauth.github;

import com.pi.ai.oauth.spi.OAuthCredentials;
import com.pi.ai.oauth.spi.OAuthLoginCallbacks;
import com.pi.ai.oauth.spi.OAuthProviderInterface;

import java.util.concurrent.CompletableFuture;

/**
 * GitHub Copilot OAuth Provider（设备码认证流程）。
 *
 * <p>使用 GitHub 的设备码认证流程（Device Code Flow），
 * 用户在浏览器中输入设备码完成认证。
 */
public class GitHubCopilotOAuthProvider implements OAuthProviderInterface {

    public static final String ID = "github-copilot";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "GitHub Copilot"; }

    @Override
    public CompletableFuture<OAuthCredentials> login(OAuthLoginCallbacks callbacks) {
        // GitHub Device Code Flow:
        // 1. Request device code from GitHub
        // 2. Show user the verification URL and code
        // 3. Poll for token completion
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("GitHub Copilot OAuth login requires device code flow"));
    }

    @Override
    public CompletableFuture<OAuthCredentials> refreshToken(OAuthCredentials credentials) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("GitHub Copilot OAuth refresh not yet implemented"));
    }

    @Override
    public String getApiKey(OAuthCredentials credentials) {
        return credentials.getAccess();
    }
}
