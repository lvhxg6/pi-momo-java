package com.pi.coding.auth;

import java.util.concurrent.CompletableFuture;

/**
 * Callbacks for OAuth login flow.
 */
public interface OAuthLoginCallbacks {
    
    /**
     * Called to open the authorization URL in a browser.
     * 
     * @param authUrl the authorization URL to open
     * @return a future that completes when the URL has been opened
     */
    CompletableFuture<Void> openAuthUrl(String authUrl);
    
    /**
     * Called to receive the authorization code from the callback.
     * 
     * @return a future that completes with the authorization code
     */
    CompletableFuture<String> receiveAuthCode();
    
    /**
     * Called when login is successful.
     * 
     * @param credential the obtained OAuth credential
     */
    default void onSuccess(OAuthCredential credential) {}
    
    /**
     * Called when login fails.
     * 
     * @param error the error that occurred
     */
    default void onError(Throwable error) {}
}
