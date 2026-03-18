package com.pi.agent.types;

import com.pi.agent.config.AfterToolCallHook;
import com.pi.agent.config.BeforeToolCallHook;
import com.pi.agent.config.ConvertToLlmFunction;
import com.pi.agent.config.GetApiKeyFunction;
import com.pi.agent.config.StreamFn;
import com.pi.agent.config.TransformContextFunction;
import com.pi.ai.core.types.PayloadInterceptor;
import com.pi.ai.core.types.ThinkingBudgets;
import com.pi.ai.core.types.Transport;

/**
 * Configuration object passed to the Agent constructor.
 *
 * <p>All fields are optional (nullable). Use {@link #builder()} to construct instances:
 * <pre>{@code
 * AgentOptions opts = AgentOptions.builder()
 *     .steeringMode(QueueMode.ALL)
 *     .toolExecution(ToolExecutionMode.SEQUENTIAL)
 *     .maxRetryDelayMs(30000)
 *     .build();
 * }</pre>
 *
 * <p><b>Validates: Requirements 23.1, 23.2</b>
 */
public class AgentOptions {

    private final AgentState initialState;
    private final ConvertToLlmFunction convertToLlm;
    private final TransformContextFunction transformContext;
    private final QueueMode steeringMode;
    private final QueueMode followUpMode;
    private final StreamFn streamFn;
    private final String sessionId;
    private final GetApiKeyFunction getApiKey;
    private final PayloadInterceptor onPayload;
    private final ThinkingBudgets thinkingBudgets;
    private final Transport transport;
    private final Integer maxRetryDelayMs;
    private final ToolExecutionMode toolExecution;
    private final BeforeToolCallHook beforeToolCall;
    private final AfterToolCallHook afterToolCall;

    private AgentOptions(Builder builder) {
        this.initialState = builder.initialState;
        this.convertToLlm = builder.convertToLlm;
        this.transformContext = builder.transformContext;
        this.steeringMode = builder.steeringMode;
        this.followUpMode = builder.followUpMode;
        this.streamFn = builder.streamFn;
        this.sessionId = builder.sessionId;
        this.getApiKey = builder.getApiKey;
        this.onPayload = builder.onPayload;
        this.thinkingBudgets = builder.thinkingBudgets;
        this.transport = builder.transport;
        this.maxRetryDelayMs = builder.maxRetryDelayMs;
        this.toolExecution = builder.toolExecution;
        this.beforeToolCall = builder.beforeToolCall;
        this.afterToolCall = builder.afterToolCall;
    }

    // --- Getters ---

    /** Returns the partial initial state, or {@code null}. */
    public AgentState getInitialState() { return initialState; }

    /** Returns the convertToLlm callback, or {@code null}. */
    public ConvertToLlmFunction getConvertToLlm() { return convertToLlm; }

    /** Returns the transformContext callback, or {@code null}. */
    public TransformContextFunction getTransformContext() { return transformContext; }

    /** Returns the steering queue mode, or {@code null}. */
    public QueueMode getSteeringMode() { return steeringMode; }

    /** Returns the follow-up queue mode, or {@code null}. */
    public QueueMode getFollowUpMode() { return followUpMode; }

    /** Returns the custom stream function, or {@code null}. */
    public StreamFn getStreamFn() { return streamFn; }

    /** Returns the session identifier, or {@code null}. */
    public String getSessionId() { return sessionId; }

    /** Returns the getApiKey callback, or {@code null}. */
    public GetApiKeyFunction getGetApiKey() { return getApiKey; }

    /** Returns the payload interceptor, or {@code null}. */
    public PayloadInterceptor getOnPayload() { return onPayload; }

    /** Returns the thinking budgets, or {@code null}. */
    public ThinkingBudgets getThinkingBudgets() { return thinkingBudgets; }

    /** Returns the transport, or {@code null}. */
    public Transport getTransport() { return transport; }

    /** Returns the max retry delay in milliseconds, or {@code null}. */
    public Integer getMaxRetryDelayMs() { return maxRetryDelayMs; }

    /** Returns the tool execution mode, or {@code null}. */
    public ToolExecutionMode getToolExecution() { return toolExecution; }

    /** Returns the beforeToolCall hook, or {@code null}. */
    public BeforeToolCallHook getBeforeToolCall() { return beforeToolCall; }

    /** Returns the afterToolCall hook, or {@code null}. */
    public AfterToolCallHook getAfterToolCall() { return afterToolCall; }

    // --- Builder ---

    /** Creates a new {@link Builder} for {@link AgentOptions}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AgentOptions}. All fields are optional.
     */
    public static final class Builder {

        private AgentState initialState;
        private ConvertToLlmFunction convertToLlm;
        private TransformContextFunction transformContext;
        private QueueMode steeringMode;
        private QueueMode followUpMode;
        private StreamFn streamFn;
        private String sessionId;
        private GetApiKeyFunction getApiKey;
        private PayloadInterceptor onPayload;
        private ThinkingBudgets thinkingBudgets;
        private Transport transport;
        private Integer maxRetryDelayMs;
        private ToolExecutionMode toolExecution;
        private BeforeToolCallHook beforeToolCall;
        private AfterToolCallHook afterToolCall;

        Builder() {}

        public Builder initialState(AgentState initialState) {
            this.initialState = initialState;
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

        public Builder steeringMode(QueueMode steeringMode) {
            this.steeringMode = steeringMode;
            return this;
        }

        public Builder followUpMode(QueueMode followUpMode) {
            this.followUpMode = followUpMode;
            return this;
        }

        public Builder streamFn(StreamFn streamFn) {
            this.streamFn = streamFn;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder getApiKey(GetApiKeyFunction getApiKey) {
            this.getApiKey = getApiKey;
            return this;
        }

        public Builder onPayload(PayloadInterceptor onPayload) {
            this.onPayload = onPayload;
            return this;
        }

        public Builder thinkingBudgets(ThinkingBudgets thinkingBudgets) {
            this.thinkingBudgets = thinkingBudgets;
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

        /** Builds the {@link AgentOptions} instance. */
        public AgentOptions build() {
            return new AgentOptions(this);
        }
    }
}
