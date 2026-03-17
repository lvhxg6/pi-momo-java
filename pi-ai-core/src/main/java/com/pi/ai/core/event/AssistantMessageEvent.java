package com.pi.ai.core.event;

import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.StopReason;
import com.pi.ai.core.types.ToolCall;

/**
 * LLM 流式响应事件的统一 sealed interface，共 12 种事件类型。
 *
 * <p>事件按生命周期分为三类：
 * <ul>
 *   <li>流开始事件：{@link Start}</li>
 *   <li>内容事件：text_start/delta/end、thinking_start/delta/end、toolcall_start/delta/end</li>
 *   <li>终止事件：{@link Done}（正常完成）、{@link Error}（错误终止）</li>
 * </ul>
 */
public sealed interface AssistantMessageEvent
        permits AssistantMessageEvent.Start,
                AssistantMessageEvent.TextStart,
                AssistantMessageEvent.TextDelta,
                AssistantMessageEvent.TextEnd,
                AssistantMessageEvent.ThinkingStart,
                AssistantMessageEvent.ThinkingDelta,
                AssistantMessageEvent.ThinkingEnd,
                AssistantMessageEvent.ToolCallStart,
                AssistantMessageEvent.ToolCallDelta,
                AssistantMessageEvent.ToolCallEnd,
                AssistantMessageEvent.Done,
                AssistantMessageEvent.Error {

    /**
     * 事件类型标识符。
     */
    String type();

    /** 流开始事件，携带初始的 partial AssistantMessage */
    record Start(AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "start"; }
    }

    /** 文本内容块开始事件 */
    record TextStart(int contentIndex, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "text_start"; }
    }

    /** 文本增量事件，携带新增的文本片段 */
    record TextDelta(int contentIndex, String delta, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "text_delta"; }
    }

    /** 文本内容块结束事件，携带完整的文本内容 */
    record TextEnd(int contentIndex, String content, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "text_end"; }
    }

    /** 思考内容块开始事件 */
    record ThinkingStart(int contentIndex, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "thinking_start"; }
    }

    /** 思考增量事件，携带新增的思考文本片段 */
    record ThinkingDelta(int contentIndex, String delta, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "thinking_delta"; }
    }

    /** 思考内容块结束事件，携带完整的思考内容 */
    record ThinkingEnd(int contentIndex, String content, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "thinking_end"; }
    }

    /** 工具调用内容块开始事件 */
    record ToolCallStart(int contentIndex, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "toolcall_start"; }
    }

    /** 工具调用增量事件，携带新增的参数 JSON 片段 */
    record ToolCallDelta(int contentIndex, String delta, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "toolcall_delta"; }
    }

    /** 工具调用内容块结束事件，携带完整的 ToolCall */
    record ToolCallEnd(int contentIndex, ToolCall toolCall, AssistantMessage partial) implements AssistantMessageEvent {
        @Override
        public String type() { return "toolcall_end"; }
    }

    /** 正常完成事件，携带停止原因和最终 AssistantMessage */
    record Done(StopReason reason, AssistantMessage message) implements AssistantMessageEvent {
        @Override
        public String type() { return "done"; }
    }

    /** 错误终止事件，携带错误原因和包含错误信息的 AssistantMessage */
    record Error(StopReason reason, AssistantMessage error) implements AssistantMessageEvent {
        @Override
        public String type() { return "error"; }
    }
}
