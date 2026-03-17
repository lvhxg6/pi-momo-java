package com.pi.ai.oauth.registry;

import com.pi.ai.oauth.spi.OAuthCredentials;
import com.pi.ai.oauth.spi.OAuthProviderInterface;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth Provider 注册表。
 *
 * <p>管理 OAuth Provider 的注册、查找、注销和 token 刷新。
 * 内置 Provider 注销后会恢复为默认实现。
 *
 * <p>对应 pi-mono 的 oauthProviderRegistry。
 */
public final class OAuthProviderRegistry {

    private static final Map<String, OAuthProviderInterface> registry = new ConcurrentHashMap<>();
    private static final List<OAuthProviderInterface> builtInProviders = new ArrayList<>();

    private OAuthProviderRegistry() {}

    /**
     * 注册内置 OAuth Provider（初始化时调用）。
     */
    public static void registerBuiltIn(OAuthProviderInterface provider) {
        builtInProviders.add(provider);
        registry.put(provider.id(), provider);
    }

    /**
     * 注册自定义 OAuth Provider。
     */
    public static void register(OAuthProviderInterface provider) {
        registry.put(provider.id(), provider);
    }

    /**
     * 获取 OAuth Provider。
     */
    public static OAuthProviderInterface get(String id) {
        return registry.get(id);
    }

    /**
     * 获取所有已注册的 OAuth Provider。
     */
    public static List<OAuthProviderInterface> getAll() {
        return List.copyOf(registry.values());
    }

    /**
     * 注销 OAuth Provider。
     * 如果是内置 Provider，恢复为默认实现。
     */
    public static void unregister(String id) {
        OAuthProviderInterface builtIn = builtInProviders.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElse(null);
        if (builtIn != null) {
            registry.put(id, builtIn);
        } else {
            registry.remove(id);
        }
    }

    /**
     * 重置为内置 Provider。
     */
    public static void reset() {
        registry.clear();
        for (OAuthProviderInterface provider : builtInProviders) {
            registry.put(provider.id(), provider);
        }
    }

    /**
     * 获取 OAuth API Key，自动刷新过期 token。
     *
     * @param providerId  Provider ID
     * @param credentials 所有 Provider 的凭证映射
     * @return API Key 和更新后的凭证，如果无凭证返回 null
     */
    public static CompletableFuture<OAuthApiKeyResult> getOAuthApiKey(
            String providerId, Map<String, OAuthCredentials> credentials) {
        OAuthProviderInterface provider = get(providerId);
        if (provider == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown OAuth provider: " + providerId));
        }

        OAuthCredentials creds = credentials.get(providerId);
        if (creds == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!creds.isExpired()) {
            String apiKey = provider.getApiKey(creds);
            return CompletableFuture.completedFuture(new OAuthApiKeyResult(creds, apiKey));
        }

        // Refresh expired token
        return provider.refreshToken(creds)
                .thenApply(newCreds -> {
                    String apiKey = provider.getApiKey(newCreds);
                    return new OAuthApiKeyResult(newCreds, apiKey);
                })
                .exceptionally(e -> {
                    throw new RuntimeException(
                            "Failed to refresh OAuth token for " + providerId, e);
                });
    }

    /**
     * OAuth API Key 结果。
     */
    public record OAuthApiKeyResult(OAuthCredentials newCredentials, String apiKey) {}
}
