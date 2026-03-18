package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OAuth credentials returned from login flow.
 *
 * @param accessToken  the access token
 * @param refreshToken the refresh token (may be null)
 * @param expiresAt    expiration timestamp in milliseconds (may be null)
 * @param extra        additional provider-specific data (may be null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthCredentials(
    @JsonProperty("accessToken") String accessToken,
    @JsonProperty("refreshToken") String refreshToken,
    @JsonProperty("expiresAt") Long expiresAt,
    @JsonProperty("extra") Map<String, Object> extra
) {

    /**
     * Builder for creating OAuthCredentials instances.
     */
    public static class Builder {
        private String accessToken;
        private String refreshToken;
        private Long expiresAt;
        private Map<String, Object> extra;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder expiresAt(Long expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder extra(Map<String, Object> extra) {
            this.extra = extra;
            return this;
        }

        public OAuthCredentials build() {
            return new OAuthCredentials(accessToken, refreshToken, expiresAt, extra);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
