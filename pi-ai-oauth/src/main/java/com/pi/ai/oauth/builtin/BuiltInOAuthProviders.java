package com.pi.ai.oauth.builtin;

import com.pi.ai.oauth.anthropic.AnthropicOAuthProvider;
import com.pi.ai.oauth.github.GitHubCopilotOAuthProvider;
import com.pi.ai.oauth.google.AntigravityOAuthProvider;
import com.pi.ai.oauth.google.GeminiCliOAuthProvider;
import com.pi.ai.oauth.openai.OpenAICodexOAuthProvider;
import com.pi.ai.oauth.registry.OAuthProviderRegistry;

/**
 * 内置 OAuth Provider 注册管理。
 *
 * <p>注册全部 5 个内置 OAuth Provider，对应 pi-mono 的 builtInOAuthProviders。
 */
public final class BuiltInOAuthProviders {

    private BuiltInOAuthProviders() {}

    /**
     * 注册全部内置 OAuth Provider。
     */
    public static void registerBuiltInOAuthProviders() {
        OAuthProviderRegistry.registerBuiltIn(new AnthropicOAuthProvider());
        OAuthProviderRegistry.registerBuiltIn(new GitHubCopilotOAuthProvider());
        OAuthProviderRegistry.registerBuiltIn(new GeminiCliOAuthProvider());
        OAuthProviderRegistry.registerBuiltIn(new AntigravityOAuthProvider());
        OAuthProviderRegistry.registerBuiltIn(new OpenAICodexOAuthProvider());
    }

    /**
     * 清空所有 OAuth Provider 后重新注册内置 Provider。
     */
    public static void resetOAuthProviders() {
        OAuthProviderRegistry.reset();
    }
}
