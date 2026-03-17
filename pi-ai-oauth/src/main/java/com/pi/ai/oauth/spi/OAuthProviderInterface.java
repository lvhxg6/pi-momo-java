package com.pi.ai.oauth.spi;

import com.pi.ai.core.types.Model;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OAuth Provider 接口，每个 OAuth 认证提供商需实现此接口。
 *
 * <p>对应 pi-mono 的 OAuthProviderInterface。
 */
public interface OAuthProviderInterface {

    /**
     * Provider 唯一标识。
     */
    String id();

    /**
     * Provider 显示名称。
     */
    String name();

    /**
     * 执行登录流程，返回凭证。
     */
    CompletableFuture<OAuthCredentials> login(OAuthLoginCallbacks callbacks);

    /**
     * 是否使用本地回调服务器（支持手动输入授权码）。
     */
    default boolean usesCallbackServer() {
        return false;
    }

    /**
     * 刷新过期凭证，返回更新后的凭证。
     */
    CompletableFuture<OAuthCredentials> refreshToken(OAuthCredentials credentials);

    /**
     * 将凭证转换为 API Key 字符串。
     */
    String getApiKey(OAuthCredentials credentials);

    /**
     * 可选：修改模型列表（如更新 baseUrl）。
     */
    default List<Model> modifyModels(List<Model> models, OAuthCredentials credentials) {
        return models;
    }
}
