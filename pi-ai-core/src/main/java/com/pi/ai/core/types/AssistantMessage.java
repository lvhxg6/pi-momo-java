package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LLM assistant response message. Uses a mutable class (not record) because
 * content, usage, and other fields are accumulated incrementally during streaming.
 *
 * <p>Construct via {@link Builder}:
 * <pre>{@code
 * AssistantMessage msg = AssistantMessage.builder()
 *     .content(List.of(new TextContent("hello")))
 *     .api("anthropic-messages")
 *     .provider("anthropic")
 *     .model("claude-3-opus")
 *     .usage(usage)
 *     .stopReason(StopReason.STOP)
 *     .timestamp(System.currentTimeMillis())
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AssistantMessage implements Message {

    @JsonProperty("role")
    private final String role = "assistant";

    @JsonProperty("content")
    private List<AssistantContentBlock> content;

    @JsonProperty("api")
    private String api;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("model")
    private String model;

    @JsonProperty("usage")
    private Usage usage;

    @JsonProperty("stopReason")
    private StopReason stopReason;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("timestamp")
    private long timestamp;

    /**
     * Default constructor for Jackson deserialization.
     */
    public AssistantMessage() {
    }

    private AssistantMessage(Builder builder) {
        this.content = builder.content;
        this.api = builder.api;
        this.provider = builder.provider;
        this.model = builder.model;
        this.usage = builder.usage;
        this.stopReason = builder.stopReason;
        this.errorMessage = builder.errorMessage;
        this.timestamp = builder.timestamp;
    }

    // --- Message interface ---

    @Override
    public String role() {
        return role;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    // --- Getters ---

    public String getRole() {
        return role;
    }

    public List<AssistantContentBlock> getContent() {
        return content;
    }

    public String getApi() {
        return api;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Usage getUsage() {
        return usage;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // --- Setters for streaming mutation ---

    public void setContent(List<AssistantContentBlock> content) {
        this.content = content;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // --- equals / hashCode ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssistantMessage that)) return false;
        return timestamp == that.timestamp
            && Objects.equals(role, that.role)
            && Objects.equals(content, that.content)
            && Objects.equals(api, that.api)
            && Objects.equals(provider, that.provider)
            && Objects.equals(model, that.model)
            && Objects.equals(usage, that.usage)
            && stopReason == that.stopReason
            && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content, api, provider, model, usage, stopReason, errorMessage, timestamp);
    }

    @Override
    public String toString() {
        return "AssistantMessage{" +
            "role='" + role + '\'' +
            ", content=" + content +
            ", api='" + api + '\'' +
            ", provider='" + provider + '\'' +
            ", model='" + model + '\'' +
            ", usage=" + usage +
            ", stopReason=" + stopReason +
            ", errorMessage='" + errorMessage + '\'' +
            ", timestamp=" + timestamp +
            '}';
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<AssistantContentBlock> content = new ArrayList<>();
        private String api;
        private String provider;
        private String model;
        private Usage usage;
        private StopReason stopReason;
        private String errorMessage;
        private long timestamp;

        private Builder() {
        }

        public Builder content(List<AssistantContentBlock> content) {
            this.content = content;
            return this;
        }

        public Builder api(String api) {
            this.api = api;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public Builder stopReason(StopReason stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AssistantMessage build() {
            return new AssistantMessage(this);
        }
    }
}
