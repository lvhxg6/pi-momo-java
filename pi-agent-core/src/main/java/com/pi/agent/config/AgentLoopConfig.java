package com.pi.agent.config;

import com.pi.agent.types.ToolExecutionMode;
import com.pi.ai.core.types.CacheRetention;
import com.pi.ai.core.types.CancellationSignal;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.PayloadInterceptor;
import com.pi.ai.core.types.SimpleStreamOptions;
import com.pi.ai.core.types.ThinkingBudgets;
import com.pi.ai.core.types.ThinkingLevel;
import com.pi.ai.core.types.Transport;

import java.util.Map;

/**
 * Configuration for the agent loop, combining {@link SimpleStreamOptions}
 * (via composition) with agent-specific fields such as model, callbacks,
 * and tool execution hooks.
 *
 * <p>Use {@link #builder()} to construct instances:
 * <pre>{@code
 * AgentLoopConfig config = AgentLoopConfig.builder()
 *     .model(myModel)
 *     .convertToLlm(msgs -> ...)
 *     .temperature(0.7)
 *     .maxTokens(4096)
 *     .toolExecution(ToolExecutionMode.PARALLEL)
 *     .build();
 * }</pre>
 *
 * <p><b>Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5, 13.6,
 * 13.7, 13.8, 13.9, 13.10, 13.11</b>
 */
public class AgentLoopConfig {

    // Composed SimpleStreamOptions instance (Req 13.1)
    private final SimpleStreamOptions streamOptions;

    // Agent-specific fields
    private final Model model;                                        // Req 13.2
    private final ConvertToLlmFunction convertToLlm;                  // Req 13.3, 13.4
    private final TransformContextFunction transformContext;           // Req 13.5, 13.6
    private final GetApiKeyFunction getApiKey;                        // Req 13.7
    private final GetSteeringMessagesFunction getSteeringMessages;    // Req 13.8
    private final GetFollowUpMessagesFunction getFollowUpMessages;    // Req 13.9
    private final ToolExecutionMode toolExecution;                    // Req 13.10
    private final BeforeToolCallHook beforeToolCall;                  // Req 13.11
    private final AfterToolCallHook afterToolCall;                    // Req 13.11

    private AgentLoopConfig(Builder builder) {
        this.streamOptions = builder.buildStreamOptions();
        this.model = builder.model;
        this.convertToLlm = builder.convertToLlm;
        this.transformContext = builder.transformContext;
        this.getApiKey = builder.getApiKey;
        this.getSteeringMessages = builder.getSteeringMessages;
        this.getFollowUpMessages = builder.getFollowUpMessages;
        this.toolExecution = builder.toolExecution != null
                ? builder.toolExecution
                : ToolExecutionMode.PARALLEL;
        this.beforeToolCall = builder.beforeToolCall;
        this.afterToolCall = builder.afterToolCall;
    }

    // --- Getters ---

    /** Returns the composed {@link SimpleStreamOptions} instance. */
    public SimpleStreamOptions getStreamOptions() { return streamOptions; }

    /** Returns the target LLM model. */
    public Model getModel() { return model; }

    /** Returns the convertToLlm callback. */
    public ConvertToLlmFunction getConvertToLlm() { return convertToLlm; }

    /** Returns the optional transformContext callback, or {@code null}. */
    public TransformContextFunction getTransformContext() { return transformContext; }

    /** Returns the optional getApiKey callback, or {@code null}. */
    public GetApiKeyFunction getGetApiKey() { return getApiKey; }

    /** Returns the optional getSteeringMessages callback, or {@code null}. */
    public GetSteeringMessagesFunction getGetSteeringMessages() { return getSteeringMessages; }

    /** Returns the optional getFollowUpMessages callback, or {@code null}. */
    public GetFollowUpMessagesFunction getGetFollowUpMessages() { return getFollowUpMessages; }

    /** Returns the tool execution mode (defaults to {@link ToolExecutionMode#PARALLEL}). */
    public ToolExecutionMode getToolExecution() { return toolExecution; }

    /** Returns the optional beforeToolCall hook, or {@code null}. */
    public BeforeToolCallHook getBeforeToolCall() { return beforeToolCall; }

    /** Returns the optional afterToolCall hook, or {@code null}. */
    public AfterToolCallHook getAfterToolCall() { return afterToolCall; }

    // --- Builder ---

    /** Creates a new {@link Builder} for {@link AgentLoopConfig}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AgentLoopConfig}.
     *
     * <p>Exposes setters for all {@link SimpleStreamOptions} fields
     * (temperature, maxTokens, apiKey, reasoning, thinkingBudgets, etc.)
     * as well as all agent-specific fields (model, convertToLlm, hooks, etc.).
     *
     * <p>The builder internally constructs a {@link SimpleStreamOptions}
     * from the stream-related fields and passes it to the config.
     */
    public static final class Builder {

        // SimpleStreamOptions fields
        private Double temperature;
        private Integer maxTokens;
        private String apiKey;
        private CacheRetention cacheRetention;
        private String sessionId;
        private Map<String, String> headers;
        private Transport transport;
        private Integer maxRetryDelayMs;
        private Map<String, Object> metadata;
        private PayloadInterceptor onPayload;
        private CancellationSignal signal;
        private ThinkingLevel reasoning;
        private ThinkingBudgets thinkingBudgets;

        // Agent-specific fields
        private Model model;
        private ConvertToLlmFunction convertToLlm;
        private TransformContextFunction transformContext;
        private GetApiKeyFunction getApiKey;
        private GetSteeringMessagesFunction getSteeringMessages;
        private GetFollowUpMessagesFunction getFollowUpMessages;
        private ToolExecutionMode toolExecution;
        private BeforeToolCallHook beforeToolCall;
        private AfterToolCallHook afterToolCall;

        Builder() {}

        // --- SimpleStreamOptions field setters ---

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder cacheRetention(CacheRetention cacheRetention) {
            this.cacheRetention = cacheRetention;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public Builder maxRetryDelayMs(Integer maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder onPayload(PayloadInterceptor onPayload) {
            this.onPayload = onPayload;
            return this;
        }

        public Builder signal(CancellationSignal signal) {
            this.signal = signal;
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

        // --- Agent-specific field setters ---

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder convertToLlm(ConvertToLlmFunction convertToLlm) {
            this.convertToLlm = convertToLlm;
            return this;
        }

        public Builder transformContext(TransformContextFunction transformContext) {
            this.transformContext = transformContext;
            return this;
        }

        public Builder getApiKey(GetApiKeyFunction getApiKey) {
            this.getApiKey = getApiKey;
            return this;
        }

        public Builder getSteeringMessages(GetSteeringMessagesFunction getSteeringMessages) {
            this.getSteeringMessages = getSteeringMessages;
            return this;
        }

        public Builder getFollowUpMessages(GetFollowUpMessagesFunction getFollowUpMessages) {
            this.getFollowUpMessages = getFollowUpMessages;
            return this;
        }

        public Builder toolExecution(ToolExecutionMode toolExecution) {
            this.toolExecution = toolExecution;
            return this;
        }

        public Builder beforeToolCall(BeforeToolCallHook beforeToolCall) {
            this.beforeToolCall = beforeToolCall;
            return this;
        }

        public Builder afterToolCall(AfterToolCallHook afterToolCall) {
            this.afterToolCall = afterToolCall;
            return this;
        }

        /** Builds the {@link AgentLoopConfig} instance. */
        public AgentLoopConfig build() {
            return new AgentLoopConfig(this);
        }

        /** Builds the internal {@link SimpleStreamOptions} from stream-related fields. */
        SimpleStreamOptions buildStreamOptions() {
            return SimpleStreamOptions.simpleBuilder()
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .apiKey(apiKey)
                    .cacheRetention(cacheRetention)
                    .sessionId(sessionId)
                    .headers(headers)
                    .transport(transport)
                    .maxRetryDelayMs(maxRetryDelayMs)
                    .metadata(metadata)
                    .onPayload(onPayload)
                    .signal(signal)
                    .reasoning(reasoning)
                    .thinkingBudgets(thinkingBudgets)
                    .build();
        }
    }
}
