package com.pi.coding.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * OAuth 2.0 credential with access token, refresh token, and expiration.
 * 
 * @param accessToken the OAuth access token
 * @param refreshToken the OAuth refresh token for token renewal
 * @param expiresAt expiration timestamp in milliseconds since epoch
 * @param extra optional additional data from the OAuth provider
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthCredential(
    String accessToken,
    String refreshToken,
    long expiresAt,
    Map<String, Object> extra
) implements AuthCredential {
    
    @JsonCreator
    public OAuthCredential(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("expiresAt") long expiresAt,
        @JsonProperty("extra") Map<String, Object> extra
    ) {
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken must not be null");
        this.refreshToken = Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        this.expiresAt = expiresAt;
        this.extra = extra;  // nullable
    }
    
    @Override
    public String type() {
        return "oauth";
    }
    
    /**
     * Checks if the access token has expired.
     * 
     * @return true if the token has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
    
    /**
     * Checks if the access token will expire within the given buffer time.
     * 
     * @param bufferMs buffer time in milliseconds
     * @return true if the token will expire within the buffer time
     */
    public boolean isExpiringSoon(long bufferMs) {
        return System.currentTimeMillis() + bufferMs >= expiresAt;
    }
    
    /**
     * Creates a new credential with an updated access token and expiration.
     * 
     * @param newAccessToken the new access token
     * @param newExpiresAt the new expiration timestamp
     * @return a new OAuthCredential with updated values
     */
    public OAuthCredential withRefreshedToken(String newAccessToken, long newExpiresAt) {
        return new OAuthCredential(newAccessToken, this.refreshToken, newExpiresAt, this.extra);
    }
}
