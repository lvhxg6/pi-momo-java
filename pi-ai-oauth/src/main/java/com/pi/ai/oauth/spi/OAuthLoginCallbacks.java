package com.pi.ai.oauth.spi;

import java.util.concurrent.CompletableFuture;

/**
 * OAuth 登录流程的回调接口。
 *
 * <p>对应 pi-mono 的 OAuthLoginCallbacks 接口。
 */
public interface OAuthLoginCallbacks {

    /**
     * 认证信息回调（如打开浏览器 URL）。
     */
    void onAuth(OAuthAuthInfo info);

    /**
     * 提示用户输入回调。
     */
    CompletableFuture<String> onPrompt(OAuthPrompt prompt);

    /**
     * 进度消息回调（可选）。
     */
    default void onProgress(String message) {}

    /**
     * 手动输入授权码回调（可选，用于无法自动回调的场景）。
     */
    default CompletableFuture<String> onManualCodeInput() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 认证信息。
     */
    record OAuthAuthInfo(String url, String instructions) {}

    /**
     * 提示信息。
     */
    record OAuthPrompt(String message, String placeholder, boolean allowEmpty) {
        public OAuthPrompt(String message) {
            this(message, null, false);
        }
    }
}
