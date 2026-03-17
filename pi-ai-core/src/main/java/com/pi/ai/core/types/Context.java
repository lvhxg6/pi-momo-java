package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 一次 LLM 调用的完整上下文，包含系统提示、消息列表和工具列表。
 *
 * <p>{@code systemPrompt} 和 {@code tools} 为可选字段（nullable），
 * 序列化时 null 值会被省略（{@code NON_NULL}）。
 *
 * @param systemPrompt 系统提示（可选）
 * @param messages     消息列表
 * @param tools        工具列表（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Context(
    @JsonProperty("systemPrompt") String systemPrompt,
    @JsonProperty("messages") List<Message> messages,
    @JsonProperty("tools") List<Tool> tools
) {

    /**
     * 仅包含消息列表的便捷构造方法。
     *
     * @param messages 消息列表
     */
    public Context(List<Message> messages) {
        this(null, messages, null);
    }

    /**
     * 包含系统提示和消息列表的便捷构造方法。
     *
     * @param systemPrompt 系统提示
     * @param messages     消息列表
     */
    public Context(String systemPrompt, List<Message> messages) {
        this(systemPrompt, messages, null);
    }

    /**
     * 创建包含所有字段的 Context 的静态工厂方法。
     *
     * @param systemPrompt 系统提示（可选）
     * @param messages     消息列表
     * @param tools        工具列表（可选）
     * @return 新的 Context 实例
     */
    public static Context of(String systemPrompt, List<Message> messages, List<Tool> tools) {
        return new Context(systemPrompt, messages, tools);
    }

    /**
     * 创建仅包含消息列表的 Context 的静态工厂方法。
     *
     * @param messages 消息列表
     * @return 新的 Context 实例
     */
    public static Context of(List<Message> messages) {
        return new Context(null, messages, null);
    }

    /**
     * 创建 Context.Builder 实例。
     *
     * @return 新的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Context 的 Builder 模式实现，支持链式调用构建 Context。
     */
    public static final class Builder {
        private String systemPrompt;
        private List<Message> messages = List.of();
        private List<Tool> tools;

        private Builder() { }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }

        public Context build() {
            return new Context(systemPrompt, messages, tools);
        }
    }
}
