package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * 流式调用的基础选项。
 *
 * <p>包含 temperature、maxTokens、apiKey 等所有 Provider 共享的调用参数。
 * 使用 Builder 模式构建，支持继承（{@link SimpleStreamOptions}）。
 *
 * <p>注意：{@code signal}（取消机制）将在 Task 3.4 中添加。
 *
 * <p>对应 TypeScript 中的 {@code StreamOptions} 接口。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamOptions {

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("maxTokens")
    private Integer maxTokens;

    @JsonProperty("apiKey")
    private String apiKey;

    @JsonProperty("cacheRetention")
    private CacheRetention cacheRetention;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("headers")
    private Map<String, String> headers;

    @JsonProperty("transport")
    private Transport transport;

    @JsonProperty("maxRetryDelayMs")
    private Integer maxRetryDelayMs;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /** 请求载荷拦截器回调，不参与 JSON 序列化。 */
    @JsonIgnore
    private PayloadInterceptor onPayload;

    /** 取消信号，用于中断正在进行的请求，不参与 JSON 序列化。 */
    @JsonIgnore
    private CancellationSignal signal;

    /** Jackson 反序列化用默认构造器。 */
    public StreamOptions() {
    }

    /** Builder 内部构造器。 */
    protected StreamOptions(AbstractBuilder<?> builder) {
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.apiKey = builder.apiKey;
        this.cacheRetention = builder.cacheRetention;
        this.sessionId = builder.sessionId;
        this.headers = builder.headers;
        this.transport = builder.transport;
        this.maxRetryDelayMs = builder.maxRetryDelayMs;
        this.metadata = builder.metadata;
        this.onPayload = builder.onPayload;
        this.signal = builder.signal;
    }

    // --- Getters ---

    public Double getTemperature() { return temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public String getApiKey() { return apiKey; }
    public CacheRetention getCacheRetention() { return cacheRetention; }
    public String getSessionId() { return sessionId; }
    public Map<String, String> getHeaders() { return headers; }
    public Transport getTransport() { return transport; }
    public Integer getMaxRetryDelayMs() { return maxRetryDelayMs; }
    public Map<String, Object> getMetadata() { return metadata; }
    public PayloadInterceptor getOnPayload() { return onPayload; }
    public CancellationSignal getSignal() { return signal; }

    // --- equals / hashCode / toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamOptions that)) return false;
        return Objects.equals(temperature, that.temperature)
            && Objects.equals(maxTokens, that.maxTokens)
            && Objects.equals(apiKey, that.apiKey)
            && cacheRetention == that.cacheRetention
            && Objects.equals(sessionId, that.sessionId)
            && Objects.equals(headers, that.headers)
            && transport == that.transport
            && Objects.equals(maxRetryDelayMs, that.maxRetryDelayMs)
            && Objects.equals(metadata, that.metadata);
        // onPayload 为函数引用，不参与 equals 比较
    }

    @Override
    public int hashCode() {
        return Objects.hash(temperature, maxTokens, apiKey, cacheRetention,
                sessionId, headers, transport, maxRetryDelayMs, metadata);
    }

    @Override
    public String toString() {
        return "StreamOptions{" +
            "temperature=" + temperature +
            ", maxTokens=" + maxTokens +
            ", apiKey='" + (apiKey != null ? "***" : null) + '\'' +
            ", cacheRetention=" + cacheRetention +
            ", sessionId='" + sessionId + '\'' +
            ", headers=" + headers +
            ", transport=" + transport +
            ", maxRetryDelayMs=" + maxRetryDelayMs +
            ", metadata=" + metadata +
            ", onPayload=" + (onPayload != null ? "<set>" : "null") +
            ", signal=" + (signal != null ? "<set>" : "null") +
            '}';
    }

    // --- Builder ---

    /** 创建 StreamOptions 的 Builder。 */
    public static Builder builder() {
        return new Builder();
    }

    /** StreamOptions 的具体 Builder。 */
    public static final class Builder extends AbstractBuilder<Builder> {
        Builder() {
        }

        @Override
        public StreamOptions build() {
            return new StreamOptions(this);
        }
    }

    /**
     * 泛型 Builder 基类，供子类（如 {@link SimpleStreamOptions}）继承扩展。
     *
     * @param <B> Builder 自身类型，用于流式 API 的类型安全返回
     */
    @SuppressWarnings("unchecked")
    public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {
        Double temperature;
        Integer maxTokens;
        String apiKey;
        CacheRetention cacheRetention;
        String sessionId;
        Map<String, String> headers;
        Transport transport;
        Integer maxRetryDelayMs;
        Map<String, Object> metadata;
        PayloadInterceptor onPayload;
        CancellationSignal signal;

        protected AbstractBuilder() {
        }

        public B temperature(Double temperature) {
            this.temperature = temperature;
            return (B) this;
        }

        public B maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return (B) this;
        }

        public B apiKey(String apiKey) {
            this.apiKey = apiKey;
            return (B) this;
        }

        public B cacheRetention(CacheRetention cacheRetention) {
            this.cacheRetention = cacheRetention;
            return (B) this;
        }

        public B sessionId(String sessionId) {
            this.sessionId = sessionId;
            return (B) this;
        }

        public B headers(Map<String, String> headers) {
            this.headers = headers;
            return (B) this;
        }

        public B transport(Transport transport) {
            this.transport = transport;
            return (B) this;
        }

        public B maxRetryDelayMs(Integer maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
            return (B) this;
        }

        public B metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return (B) this;
        }

        public B onPayload(PayloadInterceptor onPayload) {
            this.onPayload = onPayload;
            return (B) this;
        }

        public B signal(CancellationSignal signal) {
            this.signal = signal;
            return (B) this;
        }

        /** 构建选项实例。 */
        public abstract StreamOptions build();
    }
}
