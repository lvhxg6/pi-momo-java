package com.pi.agent.types;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent context container holding the system prompt, message history, and available tools.
 *
 * <p>The {@code messages} list is <b>mutable</b> ({@link ArrayList}) so that the agent loop
 * can append messages during execution. The {@code tools} list is optional and may be
 * {@code null} or empty.
 *
 * <p>Use the {@link Builder} to construct instances:
 * <pre>{@code
 * AgentContext ctx = AgentContext.builder()
 *     .systemPrompt("You are a helpful assistant.")
 *     .messages(new ArrayList<>(existingMessages))
 *     .tools(myTools)
 *     .build();
 * }</pre>
 *
 * <p><b>Validates: Requirements 9.1, 9.2</b>
 */
public class AgentContext {

    private String systemPrompt;
    private List<AgentMessage> messages;
    private List<AgentTool> tools;

    /**
     * Default constructor — initialises all fields to safe defaults.
     */
    public AgentContext() {
        this.systemPrompt = "";
        this.messages = new ArrayList<>();
        this.tools = null;
    }

    /**
     * Full constructor.
     *
     * @param systemPrompt the system prompt (defaults to empty string if {@code null})
     * @param messages     mutable message history (defaults to empty {@link ArrayList} if {@code null})
     * @param tools        available tools (may be {@code null} to indicate no tools)
     */
    public AgentContext(String systemPrompt, List<AgentMessage> messages, List<AgentTool> tools) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.tools = tools;
    }

    // --------------- Getters / Setters ---------------

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
    }

    /**
     * Returns the <b>mutable</b> message list. Callers may append directly.
     */
    public List<AgentMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AgentMessage> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * Returns the tools list, or {@code null} if no tools are configured.
     */
    public List<AgentTool> getTools() {
        return tools;
    }

    public void setTools(List<AgentTool> tools) {
        this.tools = tools;
    }

    // --------------- Builder ---------------

    /**
     * Creates a new {@link Builder} instance.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AgentContext}. Follows the same pattern used by
     * {@code Context.Builder} in pi-ai-core.
     */
    public static final class Builder {

        private String systemPrompt = "";
        private List<AgentMessage> messages;
        private List<AgentTool> tools;

        private Builder() {
        }

        /**
         * Sets the system prompt.
         *
         * @param systemPrompt the system prompt (defaults to empty string if {@code null})
         * @return this builder
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Sets the message list. The provided list is <b>copied</b> into a new
         * {@link ArrayList} to guarantee mutability.
         *
         * @param messages the initial messages
         * @return this builder
         */
        public Builder messages(List<AgentMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Sets the tools list (optional — may be {@code null}).
         *
         * @param tools the available tools
         * @return this builder
         */
        public Builder tools(List<AgentTool> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Builds the {@link AgentContext}.
         *
         * @return a new AgentContext instance with a mutable messages list
         */
        public AgentContext build() {
            AgentContext ctx = new AgentContext();
            ctx.systemPrompt = this.systemPrompt != null ? this.systemPrompt : "";
            ctx.messages = this.messages != null ? new ArrayList<>(this.messages) : new ArrayList<>();
            ctx.tools = this.tools;
            return ctx;
        }
    }
}
