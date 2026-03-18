package com.pi.coding.auth;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for OAuth provider implementations.
 * 
 * <p>Each provider (Anthropic, OpenAI, etc.) implements this interface
 * to handle provider-specific OAuth flows.
 */
public interface OAuthProvider {
    
    /**
     * Returns the provider ID.
     * 
     * @return the provider ID (e.g., "anthropic", "openai")
     */
    String providerId();
    
    /**
     * Generates the authorization URL for the OAuth flow.
     * 
     * @param state the state parameter for CSRF protection
     * @return the authorization URL
     */
    String getAuthorizationUrl(String state);
    
    /**
     * Exchanges an authorization code for tokens.
     * 
     * @param code the authorization code
     * @return a future that completes with the OAuth credential
     */
    CompletableFuture<OAuthCredential> exchangeCode(String code);
    
    /**
     * Refreshes an expired access token.
     * 
     * @param credential the current credential with refresh token
     * @return a future that completes with the refreshed credential
     */
    CompletableFuture<OAuthCredential> refreshToken(OAuthCredential credential);
}
