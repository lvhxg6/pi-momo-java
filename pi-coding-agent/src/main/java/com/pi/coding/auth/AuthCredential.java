package com.pi.coding.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for authentication credentials.
 * 
 * <p>Supports two credential types:
 * <ul>
 *   <li>{@link ApiKeyCredential} - Simple API key authentication</li>
 *   <li>{@link OAuthCredential} - OAuth 2.0 token-based authentication</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ApiKeyCredential.class, name = "apiKey"),
    @JsonSubTypes.Type(value = OAuthCredential.class, name = "oauth")
})
public sealed interface AuthCredential permits ApiKeyCredential, OAuthCredential {
    
    /**
     * Returns the credential type identifier.
     * 
     * @return "apiKey" or "oauth"
     */
    String type();
}
