package com.pi.agent.types;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent context container holding the system prompt, message history, and available tools.
 *
 * <p>The {@code messages} list is mutable so that the agent loop can append messages
 * during execution. Use the {@link Builder} to construct instances.
 *
 * <p><b>Validates: Requirements 9.1, 9.2</b>
 *
 * <p><i>Note: This is a minimal stub created for compilation. Task 3.4 will flesh out
 * the full implementation with Builder pattern.</i>
 */
public class AgentContext {

    private String systemPrompt;
    private List<AgentMessage> messages;
    private List<AgentTool> tools;

    /**
     * Default constructor for framework use.
     */
    public AgentContext() {
        this.systemPrompt = "";
        this.messages = new ArrayList<>();
        this.tools = new ArrayList<>();
    }

    /**
     * Full constructor.
     *
     * @param systemPrompt the system prompt
     * @param messages     mutable message history
     * @param tools        available tools (may be empty)
     */
    public AgentContext(String systemPrompt, List<AgentMessage> messages, List<AgentTool> tools) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.messages = messages != null ? messages : new ArrayList<>();
        this.tools = tools != null ? tools : new ArrayList<>();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AgentMessage> messages) {
        this.messages = messages;
    }

    public List<AgentTool> getTools() {
        return tools;
    }

    public void setTools(List<AgentTool> tools) {
        this.tools = tools;
    }
}
