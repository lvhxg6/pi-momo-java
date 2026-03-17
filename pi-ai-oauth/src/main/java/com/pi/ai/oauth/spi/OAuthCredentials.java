package com.pi.ai.oauth.spi;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OAuth 凭证，包含 refresh token、access token、过期时间和额外字段。
 *
 * <p>对应 pi-mono 的 OAuthCredentials 类型。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuthCredentials {

    @JsonProperty("refresh")
    private String refresh;

    @JsonProperty("access")
    private String access;

    @JsonProperty("expires")
    private long expires;

    /** 额外字段（如 provider 特定的数据） */
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public OAuthCredentials() {}

    public OAuthCredentials(String refresh, String access, long expires) {
        this.refresh = refresh;
        this.access = access;
        this.expires = expires;
    }

    public String getRefresh() { return refresh; }
    public void setRefresh(String refresh) { this.refresh = refresh; }

    public String getAccess() { return access; }
    public void setAccess(String access) { this.access = access; }

    public long getExpires() { return expires; }
    public void setExpires(long expires) { this.expires = expires; }

    @JsonAnyGetter
    public Map<String, Object> getExtra() { return extra; }

    @JsonAnySetter
    public void setExtra(String key, Object value) { extra.put(key, value); }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expires;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuthCredentials that)) return false;
        return expires == that.expires
                && Objects.equals(refresh, that.refresh)
                && Objects.equals(access, that.access)
                && Objects.equals(extra, that.extra);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refresh, access, expires, extra);
    }
}
