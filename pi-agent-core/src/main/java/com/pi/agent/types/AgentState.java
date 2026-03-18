package com.pi.agent.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.ai.core.types.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Agent runtime state, holding configuration, conversation data, and live execution status.
 *
 * <p>Thread-safety strategy (per design doc):
 * <ul>
 *   <li>{@code isStreaming} — {@code volatile} for cross-thread visibility</li>
 *   <li>{@code streamMessage} — {@code volatile} for cross-thread visibility</li>
 *   <li>{@code error} — {@code volatile} for cross-thread visibility</li>
 *   <li>{@code pendingToolCalls} — {@link CopyOnWriteArraySet} for concurrent add/remove</li>
 *   <li>{@code messages} — plain {@link ArrayList}, only modified by the Agent thread</li>
 * </ul>
 *
 * <p>Jackson serialization is supported for debugging and logging purposes.
 * The {@code tools} field is excluded from serialization because {@link AgentTool}
 * is an interface and cannot be generically deserialized.
 *
 * <p><b>Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 39.4</b>
 *
 * @see AgentThinkingLevel
 * @see AgentTool
 * @see AgentMessage
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentState {

    @JsonProperty("systemPrompt")
    private String systemPrompt;

    @JsonProperty("model")
    private Model model;

    @JsonProperty("thinkingLevel")
    private AgentThinkingLevel thinkingLevel;

    /**
     * Tools list — excluded from Jackson serialization because {@link AgentTool}
     * is an interface and cannot be generically deserialized.
     */
    @JsonIgnore
    private List<AgentTool> tools;

    @JsonProperty("messages")
    private List<AgentMessage> messages;

    /** Volatile for cross-thread visibility (Req 39.4). */
    @JsonProperty("isStreaming")
    private volatile boolean isStreaming;

    /** Volatile for cross-thread visibility. */
    @JsonProperty("streamMessage")
    private volatile AgentMessage streamMessage;

    /**
     * Thread-safe set of currently executing tool call IDs (Req 10.4, 10.5).
     */
    @JsonProperty("pendingToolCalls")
    private Set<String> pendingToolCalls;

    /** Volatile for cross-thread visibility. */
    @JsonProperty("error")
    private volatile String error;

    // --------------- Constructors ---------------

    /**
     * Default constructor — initialises all fields to safe defaults.
     *
     * <ul>
     *   <li>{@code systemPrompt} = {@code ""}</li>
     *   <li>{@code model} = {@code null}</li>
     *   <li>{@code thinkingLevel} = {@link AgentThinkingLevel#OFF}</li>
     *   <li>{@code tools} = empty list</li>
     *   <li>{@code messages} = empty mutable list</li>
     *   <li>{@code isStreaming} = {@code false}</li>
     *   <li>{@code streamMessage} = {@code null}</li>
     *   <li>{@code pendingToolCalls} = empty {@link CopyOnWriteArraySet}</li>
     *   <li>{@code error} = {@code null}</li>
     * </ul>
     */
    public AgentState() {
        this.systemPrompt = "";
        this.model = null;
        this.thinkingLevel = AgentThinkingLevel.OFF;
        this.tools = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.isStreaming = false;
        this.streamMessage = null;
        this.pendingToolCalls = new CopyOnWriteArraySet<>();
        this.error = null;
    }

    // --------------- Getters ---------------

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Model getModel() {
        return model;
    }

    public AgentThinkingLevel getThinkingLevel() {
        return thinkingLevel;
    }

    public List<AgentTool> getTools() {
        return tools;
    }

    /**
     * Returns the <b>mutable</b> message list. Only the Agent thread should modify it.
     */
    public List<AgentMessage> getMessages() {
        return messages;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public AgentMessage getStreamMessage() {
        return streamMessage;
    }

    /**
     * Returns an unmodifiable view of the pending tool call IDs.
     * The underlying {@link CopyOnWriteArraySet} is thread-safe for add/remove.
     */
    public Set<String> getPendingToolCalls() {
        return Collections.unmodifiableSet(pendingToolCalls);
    }

    public String getError() {
        return error;
    }

    // --------------- Setters ---------------

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public void setThinkingLevel(AgentThinkingLevel thinkingLevel) {
        this.thinkingLevel = thinkingLevel != null ? thinkingLevel : AgentThinkingLevel.OFF;
    }

    public void setTools(List<AgentTool> tools) {
        this.tools = tools != null ? tools : new ArrayList<>();
    }

    public void setMessages(List<AgentMessage> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    public void setIsStreaming(boolean isStreaming) {
        this.isStreaming = isStreaming;
    }

    public void setStreamMessage(AgentMessage streamMessage) {
        this.streamMessage = streamMessage;
    }

    /**
     * Replaces the entire pending tool calls set.
     *
     * @param pendingToolCalls the new set (copied into a fresh {@link CopyOnWriteArraySet})
     */
    public void setPendingToolCalls(Set<String> pendingToolCalls) {
        this.pendingToolCalls = pendingToolCalls != null
                ? new CopyOnWriteArraySet<>(pendingToolCalls)
                : new CopyOnWriteArraySet<>();
    }

    public void setError(String error) {
        this.error = error;
    }

    // --------------- Convenience methods for pendingToolCalls ---------------

    /**
     * Adds a tool call ID to the pending set (Req 10.4).
     *
     * @param toolCallId the tool call ID to add
     */
    public void addPendingToolCall(String toolCallId) {
        this.pendingToolCalls.add(toolCallId);
    }

    /**
     * Removes a tool call ID from the pending set (Req 10.5).
     *
     * @param toolCallId the tool call ID to remove
     */
    public void removePendingToolCall(String toolCallId) {
        this.pendingToolCalls.remove(toolCallId);
    }

    /**
     * Clears all pending tool call IDs.
     */
    public void clearPendingToolCalls() {
        this.pendingToolCalls.clear();
    }
}
