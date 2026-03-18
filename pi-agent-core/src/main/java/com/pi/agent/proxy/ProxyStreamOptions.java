package com.pi.agent.proxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.ai.core.types.SimpleStreamOptions;
import com.pi.ai.core.types.ThinkingBudgets;
import com.pi.ai.core.types.ThinkingLevel;

import java.util.Objects;

/**
 * 代理流配置选项，继承 {@link SimpleStreamOptions} 的所有字段，
 * 额外包含代理服务器认证和地址信息。
 *
 * <p>使用 {@link #proxyBuilder()} 创建 Builder 实例：
 * <pre>{@code
 * ProxyStreamOptions opts = ProxyStreamOptions.proxyBuilder()
 *     .authToken("my-token")
 *     .proxyUrl("https://genai.example.com")
 *     .temperature(0.7)
 *     .maxTokens(4096)
 *     .reasoning(ThinkingLevel.HIGH)
 *     .build();
 * }</pre>
 *
 * <p>对应 TypeScript 中的 {@code ProxyStreamOptions} 接口。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxyStreamOptions extends SimpleStreamOptions {

    @JsonProperty("authToken")
    private String authToken;

    @JsonProperty("proxyUrl")
    private String proxyUrl;

    /** Jackson 反序列化用默认构造器。 */
    public ProxyStreamOptions() {
    }

    private ProxyStreamOptions(Builder builder) {
        super(builder, builder.reasoning, builder.thinkingBudgets);
        this.authToken = builder.authToken;
        this.proxyUrl = builder.proxyUrl;
    }

    // --- Getters ---

    public String getAuthToken() {
        return authToken;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    // --- equals / hashCode / toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProxyStreamOptions that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(authToken, that.authToken)
            && Objects.equals(proxyUrl, that.proxyUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), authToken, proxyUrl);
    }

    @Override
    public String toString() {
        return "ProxyStreamOptions{" +
            "authToken='" + (authToken != null ? "***" : null) + '\'' +
            ", proxyUrl='" + proxyUrl + '\'' +
            ", " + super.toString() +
            '}';
    }

    // --- Builder ---

    /** 创建 ProxyStreamOptions 的 Builder。 */
    public static Builder proxyBuilder() {
        return new Builder();
    }

    /**
     * ProxyStreamOptions 的 Builder，继承 StreamOptions.AbstractBuilder 的所有方法，
     * 并额外支持 reasoning、thinkingBudgets、authToken 和 proxyUrl 字段。
     */
    public static final class Builder extends AbstractBuilder<Builder> {
        private String authToken;
        private String proxyUrl;
        private ThinkingLevel reasoning;
        private ThinkingBudgets thinkingBudgets;

        Builder() {
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder proxyUrl(String proxyUrl) {
            this.proxyUrl = proxyUrl;
            return this;
        }

        public Builder reasoning(ThinkingLevel reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder thinkingBudgets(ThinkingBudgets thinkingBudgets) {
            this.thinkingBudgets = thinkingBudgets;
            return this;
        }

        @Override
        public ProxyStreamOptions build() {
            return new ProxyStreamOptions(this);
        }
    }
}
