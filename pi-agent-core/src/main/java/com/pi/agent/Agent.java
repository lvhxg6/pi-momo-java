package com.pi.agent;

import com.pi.agent.config.AfterToolCallHook;
import com.pi.agent.config.BeforeToolCallHook;
import com.pi.agent.config.ConvertToLlmFunction;
import com.pi.agent.config.GetApiKeyFunction;
import com.pi.agent.config.StreamFn;
import com.pi.agent.config.TransformContextFunction;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.AgentOptions;
import com.pi.agent.types.AgentState;
import com.pi.agent.types.AgentThinkingLevel;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.MessageAdapter;
import com.pi.agent.types.QueueMode;
import com.pi.agent.types.ToolExecutionMode;
import com.pi.ai.core.stream.PiAi;
import com.pi.ai.core.types.CancellationSignal;
import com.pi.ai.core.types.Message;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.PayloadInterceptor;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.ThinkingBudgets;
import com.pi.ai.core.types.Transport;
import com.pi.ai.core.types.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * High-level Agent class that manages state, event subscriptions, message queues,
 * and lifecycle control for LLM-powered agent interactions.
 *
 * <p>Thread safety strategy (per design doc):
 * <ul>
 *   <li>{@code isStreaming} — volatile boolean in AgentState</li>
 *   <li>{@code listeners} — {@link CopyOnWriteArraySet} for concurrent subscribe/unsubscribe</li>
 *   <li>{@code steeringQueue} — {@link ConcurrentLinkedQueue} for thread-safe enqueue</li>
 *   <li>{@code followUpQueue} — {@link ConcurrentLinkedQueue} for thread-safe enqueue</li>
 *   <li>{@code signal} — volatile reference for cross-thread cancellation</li>
 *   <li>{@code runningPromise} — volatile reference for waitForIdle</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7, 23.8, 23.9</b>
 *
 * @see AgentOptions
 * @see AgentState
 * @see AgentEvent
 */
public class Agent {

    // ─────────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────────

    /** The agent's runtime state. */
    private final AgentState state;

    /** Event listeners (thread-safe for concurrent subscribe/unsubscribe). */
    private final Set<Consumer<AgentEvent>> listeners;

    /** Steering message queue (thread-safe for cross-thread enqueue). */
    private final ConcurrentLinkedQueue<AgentMessage> steeringQueue;

    /** Follow-up message queue (thread-safe for cross-thread enqueue). */
    private final ConcurrentLinkedQueue<AgentMessage> followUpQueue;

    /** Current cancellation signal (volatile for cross-thread visibility). */
    private volatile CancellationSignal signal;

    /** Running loop promise (volatile for waitForIdle). */
    private volatile CompletableFuture<Void> runningPromise;

    // ─────────────────────────────────────────────────────────────────────────────
    // Configuration (from AgentOptions)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Message conversion function for LLM calls. */
    private ConvertToLlmFunction convertToLlm;

    /** Optional context transformation function. */
    private TransformContextFunction transformContext;

    /** Steering queue dequeue mode. */
    private QueueMode steeringMode;

    /** Follow-up queue dequeue mode. */
    private QueueMode followUpMode;

    /** Stream function for LLM calls. */
    private StreamFn streamFn;

    /** Session identifier for provider caching. */
    private String sessionId;

    /** Dynamic API key resolver. */
    private GetApiKeyFunction getApiKey;

    /** Payload interceptor for debugging. */
    private PayloadInterceptor onPayload;

    /** Thinking budgets configuration. */
    private ThinkingBudgets thinkingBudgets;

    /** Transport protocol. */
    private Transport transport;

    /** Maximum retry delay in milliseconds. */
    private Integer maxRetryDelayMs;

    /** Tool execution mode (sequential or parallel). */
    private ToolExecutionMode toolExecution;

    /** Before tool call hook. */
    private BeforeToolCallHook beforeToolCall;

    /** After tool call hook. */
    private AfterToolCallHook afterToolCall;

    // ─────────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new Agent with the given options.
     *
     * <p>All fields in {@link AgentOptions} are optional. When not provided:
     * <ul>
     *   <li>{@code initialState} — uses default state (systemPrompt="", thinkingLevel=OFF,
     *       tools=[], messages=[], isStreaming=false)</li>
     *   <li>{@code convertToLlm} — uses default converter that keeps only user/assistant/toolResult messages</li>
     *   <li>{@code steeringMode} — defaults to {@link QueueMode#ONE_AT_A_TIME}</li>
     *   <li>{@code followUpMode} — defaults to {@link QueueMode#ONE_AT_A_TIME}</li>
     *   <li>{@code streamFn} — defaults to {@link PiAi#streamSimple}</li>
     *   <li>{@code transport} — defaults to {@link Transport#SSE}</li>
     *   <li>{@code toolExecution} — defaults to {@link ToolExecutionMode#PARALLEL}</li>
     * </ul>
     *
     * @param options configuration options (can be null for all defaults)
     */
    public Agent(AgentOptions options) {
        // Initialize thread-safe collections
        this.listeners = new CopyOnWriteArraySet<>();
        this.steeringQueue = new ConcurrentLinkedQueue<>();
        this.followUpQueue = new ConcurrentLinkedQueue<>();
        this.signal = null;
        this.runningPromise = null;

        // Initialize state from options or use defaults
        if (options != null && options.getInitialState() != null) {
            this.state = copyState(options.getInitialState());
        } else {
            this.state = createDefaultState();
        }

        // Apply configuration from options with defaults
        applyOptions(options);
    }

    /**
     * Creates a new Agent with all default settings.
     */
    public Agent() {
        this(null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Initialization helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a default AgentState with all fields initialized to safe defaults.
     *
     * <p>Default values:
     * <ul>
     *   <li>systemPrompt = ""</li>
     *   <li>model = null</li>
     *   <li>thinkingLevel = OFF</li>
     *   <li>tools = empty list</li>
     *   <li>messages = empty mutable list</li>
     *   <li>isStreaming = false</li>
     *   <li>streamMessage = null</li>
     *   <li>pendingToolCalls = empty set</li>
     *   <li>error = null</li>
     * </ul>
     */
    private AgentState createDefaultState() {
        return new AgentState();
    }

    /**
     * Creates a copy of the given state, ensuring mutable collections are copied.
     */
    private AgentState copyState(AgentState source) {
        AgentState copy = new AgentState();
        copy.setSystemPrompt(source.getSystemPrompt());
        copy.setModel(source.getModel());
        copy.setThinkingLevel(source.getThinkingLevel());
        copy.setTools(source.getTools() != null ? new ArrayList<>(source.getTools()) : new ArrayList<>());
        copy.setMessages(source.getMessages() != null ? new ArrayList<>(source.getMessages()) : new ArrayList<>());
        copy.setIsStreaming(source.isStreaming());
        copy.setStreamMessage(source.getStreamMessage());
        copy.setPendingToolCalls(source.getPendingToolCalls());
        copy.setError(source.getError());
        return copy;
    }

    /**
     * Applies configuration from AgentOptions, using defaults for null values.
     */
    private void applyOptions(AgentOptions options) {
        if (options == null) {
            // All defaults
            this.convertToLlm = createDefaultConvertToLlm();
            this.transformContext = null;
            this.steeringMode = QueueMode.ONE_AT_A_TIME;
            this.followUpMode = QueueMode.ONE_AT_A_TIME;
            this.streamFn = createDefaultStreamFn();
            this.sessionId = null;
            this.getApiKey = null;
            this.onPayload = null;
            this.thinkingBudgets = null;
            this.transport = Transport.SSE;
            this.maxRetryDelayMs = null;
            this.toolExecution = ToolExecutionMode.PARALLEL;
            this.beforeToolCall = null;
            this.afterToolCall = null;
            return;
        }

        // Apply each option with default fallback
        this.convertToLlm = options.getConvertToLlm() != null
                ? options.getConvertToLlm()
                : createDefaultConvertToLlm();

        this.transformContext = options.getTransformContext();

        this.steeringMode = options.getSteeringMode() != null
                ? options.getSteeringMode()
                : QueueMode.ONE_AT_A_TIME;

        this.followUpMode = options.getFollowUpMode() != null
                ? options.getFollowUpMode()
                : QueueMode.ONE_AT_A_TIME;

        this.streamFn = options.getStreamFn() != null
                ? options.getStreamFn()
                : createDefaultStreamFn();

        this.sessionId = options.getSessionId();
        this.getApiKey = options.getGetApiKey();
        this.onPayload = options.getOnPayload();
        this.thinkingBudgets = options.getThinkingBudgets();

        this.transport = options.getTransport() != null
                ? options.getTransport()
                : Transport.SSE;

        this.maxRetryDelayMs = options.getMaxRetryDelayMs();

        this.toolExecution = options.getToolExecution() != null
                ? options.getToolExecution()
                : ToolExecutionMode.PARALLEL;

        this.beforeToolCall = options.getBeforeToolCall();
        this.afterToolCall = options.getAfterToolCall();
    }

    /**
     * Creates the default convertToLlm function that filters messages by role.
     *
     * <p>Only keeps messages with role "user", "assistant", or "toolResult".
     * Custom message types are filtered out.
     */
    private ConvertToLlmFunction createDefaultConvertToLlm() {
        return messages -> {
            List<Message> result = new ArrayList<>();
            for (AgentMessage agentMsg : messages) {
                // Only include wrapped LLM messages (MessageAdapter instances)
                if (MessageAdapter.isLlmMessage(agentMsg)) {
                    String role = agentMsg.role();
                    // Filter to only user, assistant, and toolResult roles
                    if ("user".equals(role) || "assistant".equals(role) || "toolResult".equals(role)) {
                        result.add(MessageAdapter.unwrap(agentMsg));
                    }
                }
            }
            return result;
        };
    }

    /**
     * Creates the default streamFn that delegates to PiAi.streamSimple.
     */
    private StreamFn createDefaultStreamFn() {
        return PiAi::streamSimple;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // State accessors
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current agent state.
     *
     * @return the agent state reference
     */
    public AgentState getState() {
        return state;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // State modification methods (Req 26)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sets the system prompt.
     *
     * @param systemPrompt the new system prompt
     */
    public void setSystemPrompt(String systemPrompt) {
        state.setSystemPrompt(systemPrompt);
    }

    /**
     * Sets the model.
     *
     * @param model the new model
     */
    public void setModel(Model model) {
        state.setModel(model);
    }

    /**
     * Sets the thinking level.
     *
     * @param thinkingLevel the new thinking level
     */
    public void setThinkingLevel(AgentThinkingLevel thinkingLevel) {
        state.setThinkingLevel(thinkingLevel);
    }

    /**
     * Sets the tools list.
     *
     * @param tools the new tools list
     */
    public void setTools(List<AgentTool> tools) {
        state.setTools(tools);
    }

    /**
     * Replaces all messages with a copy of the given list.
     *
     * @param messages the new messages list
     */
    public void replaceMessages(List<AgentMessage> messages) {
        state.setMessages(messages);
    }

    /**
     * Appends a message to the messages list.
     *
     * @param message the message to append
     */
    public void appendMessage(AgentMessage message) {
        state.getMessages().add(message);
    }

    /**
     * Clears all messages.
     */
    public void clearMessages() {
        state.setMessages(new ArrayList<>());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Configuration accessors and mutators
    // ─────────────────────────────────────────────────────────────────────────────

    /** Returns the session ID. */
    public String getSessionId() {
        return sessionId;
    }

    /** Sets the session ID. */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** Returns the transport protocol. */
    public Transport getTransport() {
        return transport;
    }

    /** Sets the transport protocol. */
    public void setTransport(Transport transport) {
        this.transport = transport != null ? transport : Transport.SSE;
    }

    /** Returns the max retry delay in milliseconds. */
    public Integer getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    /** Sets the max retry delay in milliseconds. */
    public void setMaxRetryDelayMs(Integer maxRetryDelayMs) {
        this.maxRetryDelayMs = maxRetryDelayMs;
    }

    /** Returns the thinking budgets. */
    public ThinkingBudgets getThinkingBudgets() {
        return thinkingBudgets;
    }

    /** Sets the thinking budgets. */
    public void setThinkingBudgets(ThinkingBudgets thinkingBudgets) {
        this.thinkingBudgets = thinkingBudgets;
    }

    /** Returns the tool execution mode. */
    public ToolExecutionMode getToolExecution() {
        return toolExecution;
    }

    /** Sets the tool execution mode. */
    public void setToolExecution(ToolExecutionMode toolExecution) {
        this.toolExecution = toolExecution != null ? toolExecution : ToolExecutionMode.PARALLEL;
    }

    /** Sets the before tool call hook. */
    public void setBeforeToolCall(BeforeToolCallHook beforeToolCall) {
        this.beforeToolCall = beforeToolCall;
    }

    /** Sets the after tool call hook. */
    public void setAfterToolCall(AfterToolCallHook afterToolCall) {
        this.afterToolCall = afterToolCall;
    }

    /** Returns the steering mode. */
    public QueueMode getSteeringMode() {
        return steeringMode;
    }

    /** Sets the steering mode. */
    public void setSteeringMode(QueueMode steeringMode) {
        this.steeringMode = steeringMode != null ? steeringMode : QueueMode.ONE_AT_A_TIME;
    }

    /** Returns the follow-up mode. */
    public QueueMode getFollowUpMode() {
        return followUpMode;
    }

    /** Sets the follow-up mode. */
    public void setFollowUpMode(QueueMode followUpMode) {
        this.followUpMode = followUpMode != null ? followUpMode : QueueMode.ONE_AT_A_TIME;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Internal configuration accessors (for _runLoop)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Returns the convertToLlm function. */
    ConvertToLlmFunction getConvertToLlm() {
        return convertToLlm;
    }

    /** Returns the transformContext function. */
    TransformContextFunction getTransformContext() {
        return transformContext;
    }

    /** Returns the streamFn. */
    StreamFn getStreamFn() {
        return streamFn;
    }

    /** Returns the getApiKey function. */
    GetApiKeyFunction getGetApiKey() {
        return getApiKey;
    }

    /** Returns the onPayload interceptor. */
    PayloadInterceptor getOnPayload() {
        return onPayload;
    }

    /** Returns the beforeToolCall hook. */
    BeforeToolCallHook getBeforeToolCall() {
        return beforeToolCall;
    }

    /** Returns the afterToolCall hook. */
    AfterToolCallHook getAfterToolCall() {
        return afterToolCall;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Event subscription (Req 27)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Subscribes to agent events.
     *
     * @param listener the event listener
     * @return a runnable that unsubscribes the listener when called
     */
    public Runnable subscribe(Consumer<AgentEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * Emits an event to all subscribed listeners.
     *
     * @param event the event to emit
     */
    void emit(AgentEvent event) {
        for (Consumer<AgentEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Queue operations (Req 28, 29)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Adds a message to the steering queue.
     *
     * @param message the message to steer
     */
    public void steer(AgentMessage message) {
        steeringQueue.offer(message);
    }

    /**
     * Adds a message to the follow-up queue.
     *
     * @param message the message to follow up
     */
    public void followUp(AgentMessage message) {
        followUpQueue.offer(message);
    }

    /**
     * Clears the steering queue.
     */
    public void clearSteeringQueue() {
        steeringQueue.clear();
    }

    /**
     * Clears the follow-up queue.
     */
    public void clearFollowUpQueue() {
        followUpQueue.clear();
    }

    /**
     * Clears both steering and follow-up queues.
     */
    public void clearAllQueues() {
        steeringQueue.clear();
        followUpQueue.clear();
    }

    /**
     * Returns true if there are queued messages in either queue.
     *
     * @return true if steering or follow-up queue is non-empty
     */
    public boolean hasQueuedMessages() {
        return !steeringQueue.isEmpty() || !followUpQueue.isEmpty();
    }

    /**
     * Dequeues steering messages according to the steering mode.
     *
     * @return list of dequeued messages (may be empty)
     */
    List<AgentMessage> dequeueSteeringMessages() {
        if (steeringMode == QueueMode.ONE_AT_A_TIME) {
            AgentMessage first = steeringQueue.poll();
            return first != null ? List.of(first) : List.of();
        }
        // ALL mode: drain the entire queue
        List<AgentMessage> all = new ArrayList<>();
        AgentMessage msg;
        while ((msg = steeringQueue.poll()) != null) {
            all.add(msg);
        }
        return all;
    }

    /**
     * Dequeues follow-up messages according to the follow-up mode.
     *
     * @return list of dequeued messages (may be empty)
     */
    List<AgentMessage> dequeueFollowUpMessages() {
        if (followUpMode == QueueMode.ONE_AT_A_TIME) {
            AgentMessage first = followUpQueue.poll();
            return first != null ? List.of(first) : List.of();
        }
        // ALL mode: drain the entire queue
        List<AgentMessage> all = new ArrayList<>();
        AgentMessage msg;
        while ((msg = followUpQueue.poll()) != null) {
            all.add(msg);
        }
        return all;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Lifecycle control (Req 30)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Aborts the current agent loop if running.
     *
     * <p>Thread-safe: can be called from any thread.
     */
    public void abort() {
        CancellationSignal currentSignal = this.signal;
        if (currentSignal != null) {
            currentSignal.cancel();
        }
    }

    /**
     * Returns a future that completes when the agent becomes idle.
     *
     * @return a future that completes when no loop is running
     */
    public CompletableFuture<Void> waitForIdle() {
        CompletableFuture<Void> current = this.runningPromise;
        if (current != null) {
            return current;
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Resets the agent to initial state.
     *
     * <p>Clears messages, sets isStreaming to false, clears streamMessage,
     * clears pendingToolCalls, clears error, and clears all queues.
     */
    public void reset() {
        state.setMessages(new ArrayList<>());
        state.setIsStreaming(false);
        state.setStreamMessage(null);
        state.clearPendingToolCalls();
        state.setError(null);
        clearAllQueues();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Internal signal and promise accessors (for _runLoop)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Returns the current cancellation signal. */
    CancellationSignal getSignal() {
        return signal;
    }

    /** Sets the current cancellation signal. */
    void setSignal(CancellationSignal signal) {
        this.signal = signal;
    }

    /** Returns the running promise. */
    CompletableFuture<Void> getRunningPromise() {
        return runningPromise;
    }

    /** Sets the running promise. */
    void setRunningPromise(CompletableFuture<Void> runningPromise) {
        this.runningPromise = runningPromise;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Prompt and Continue methods (Req 24, 25)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sends a text prompt to the agent.
     *
     * <p>The text is wrapped in a {@link UserMessage} with {@link TextContent}.
     *
     * @param text the text prompt to send
     * @return a future that completes when the agent loop finishes
     * @throws IllegalStateException if the agent is already streaming or no model is configured
     *
     * <p><b>Validates: Requirements 24.1, 24.2, 24.4, 24.5, 24.6</b>
     */
    public CompletableFuture<Void> prompt(String text) {
        // Create UserMessage with TextContent, wrap in MessageAdapter
        UserMessage userMessage = new UserMessage(text, System.currentTimeMillis());
        AgentMessage agentMessage = MessageAdapter.wrap(userMessage);
        return prompt(agentMessage);
    }

    /**
     * Sends a single message prompt to the agent.
     *
     * @param message the message to send
     * @return a future that completes when the agent loop finishes
     * @throws IllegalStateException if the agent is already streaming or no model is configured
     *
     * <p><b>Validates: Requirements 24.1, 24.4, 24.5, 24.6</b>
     */
    public CompletableFuture<Void> prompt(AgentMessage message) {
        return prompt(List.of(message));
    }

    /**
     * Sends a list of messages as prompts to the agent.
     *
     * @param messages the messages to send
     * @return a future that completes when the agent loop finishes
     * @throws IllegalStateException if the agent is already streaming or no model is configured
     *
     * <p><b>Validates: Requirements 24.1, 24.4, 24.5, 24.6</b>
     */
    public CompletableFuture<Void> prompt(List<AgentMessage> messages) {
        // Check if already streaming (Req 24.4)
        if (state.isStreaming()) {
            throw new IllegalStateException(
                    "Agent is already processing a prompt. Use steer() or followUp() to queue messages, or wait for completion.");
        }

        // Check if model is configured (Req 24.5)
        if (state.getModel() == null) {
            throw new IllegalStateException("No model configured");
        }

        // Start the loop (Req 24.6) - placeholder for now, will be implemented in Task 10.8
        return _runLoop(messages, false);
    }

    /**
     * Continues the agent from the current context.
     *
     * <p>If there are queued steering or follow-up messages, they are processed.
     * Otherwise, the loop continues from the current message state.
     *
     * @return a future that completes when the agent loop finishes
     * @throws IllegalStateException if the agent is already streaming, no messages exist,
     *         or the last message is from assistant with no queued messages
     *
     * <p><b>Validates: Requirements 25.1, 25.2, 25.3, 25.4, 25.5, 25.6</b>
     */
    public CompletableFuture<Void> continueProcessing() {
        // Check if already streaming (Req 25.1)
        if (state.isStreaming()) {
            throw new IllegalStateException(
                    "Agent is already processing. Wait for completion before continuing.");
        }

        // Check if model is configured
        if (state.getModel() == null) {
            throw new IllegalStateException("No model configured");
        }

        // Check if messages exist (Req 25.2)
        List<AgentMessage> messages = state.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw new IllegalStateException("No messages to continue from");
        }

        // Get the last message role
        AgentMessage lastMessage = messages.get(messages.size() - 1);
        String lastRole = lastMessage.role();

        if ("assistant".equals(lastRole)) {
            // Check for queued steering messages (Req 25.3)
            List<AgentMessage> steeringMessages = dequeueSteeringMessages();
            if (!steeringMessages.isEmpty()) {
                // Start loop with steering messages as new prompts, skip first steering poll
                return _runLoop(steeringMessages, false);
            }

            // Check for queued follow-up messages (Req 25.4)
            List<AgentMessage> followUpMessages = dequeueFollowUpMessages();
            if (!followUpMessages.isEmpty()) {
                // Start loop with follow-up messages as new prompts
                return _runLoop(followUpMessages, false);
            }

            // No queued messages, cannot continue from assistant (Req 25.5)
            throw new IllegalStateException("Cannot continue from message role: assistant");
        }

        // Last message is not assistant, continue in continue mode (Req 25.6)
        return _runLoop(List.of(), true);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Internal loop management (placeholder for Task 10.8)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Internal method to run the agent loop.
     *
     * <p>This is a placeholder that will be fully implemented in Task 10.8.
     *
     * @param prompts the messages to send as prompts (empty for continue mode)
     * @param continueMode true if continuing from existing context, false for new prompts
     * @return a future that completes when the loop finishes
     */
    CompletableFuture<Void> _runLoop(List<AgentMessage> prompts, boolean continueMode) {
        // Placeholder implementation - will be fully implemented in Task 10.8
        // For now, just return a completed future
        return CompletableFuture.completedFuture(null);
    }
}
