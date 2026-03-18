package com.pi.agent.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.pi.ai.core.types.StopReason;
import com.pi.ai.core.types.Usage;

/**
 * Proxy assistant message events — bandwidth-optimized versions of {@link com.pi.ai.core.event.AssistantMessageEvent}.
 *
 * <p>Key differences from {@code AssistantMessageEvent}:
 * <ul>
 *   <li>Delta events do NOT include a {@code partial} field (bandwidth optimization)</li>
 *   <li>Start event does NOT include a {@code partial} field</li>
 *   <li>Done/Error events carry {@code reason} and {@code usage} directly instead of full message</li>
 * </ul>
 *
 * <p>Uses Jackson polymorphic serialization based on the {@code type} discriminator field.
 *
 * <p><b>Validates: Requirements 34.1, 34.2, 34.3, 34.4, 34.5, 34.6, 34.7, 34.8</b>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.Start.class, name = "start"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.TextStart.class, name = "text_start"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.TextDelta.class, name = "text_delta"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.TextEnd.class, name = "text_end"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.ThinkingStart.class, name = "thinking_start"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.ThinkingDelta.class, name = "thinking_delta"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.ThinkingEnd.class, name = "thinking_end"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.ToolCallStart.class, name = "toolcall_start"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.ToolCallDelta.class, name = "toolcall_delta"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.ToolCallEnd.class, name = "toolcall_end"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.Done.class, name = "done"),
    @JsonSubTypes.Type(value = ProxyAssistantMessageEvent.Error.class, name = "error")
})
public sealed interface ProxyAssistantMessageEvent
        permits ProxyAssistantMessageEvent.Start,
                ProxyAssistantMessageEvent.TextStart,
                ProxyAssistantMessageEvent.TextDelta,
                ProxyAssistantMessageEvent.TextEnd,
                ProxyAssistantMessageEvent.ThinkingStart,
                ProxyAssistantMessageEvent.ThinkingDelta,
                ProxyAssistantMessageEvent.ThinkingEnd,
                ProxyAssistantMessageEvent.ToolCallStart,
                ProxyAssistantMessageEvent.ToolCallDelta,
                ProxyAssistantMessageEvent.ToolCallEnd,
                ProxyAssistantMessageEvent.Done,
                ProxyAssistantMessageEvent.Error {

    /**
     * Event type discriminator string.
     */
    String type();

    // ── Stream lifecycle ─────────────────────────────────────────────────

    /** Stream start event — no partial field (Req 34.3). */
    record Start() implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "start"; }
    }

    // ── Text content events ──────────────────────────────────────────────

    /** Text content block start (Req 34.6). */
    record TextStart(int contentIndex) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "text_start"; }
    }

    /** Text delta — no partial field (Req 34.2). */
    record TextDelta(int contentIndex, String delta) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "text_delta"; }
    }

    /** Text content block end with optional contentSignature (Req 34.6, 34.7). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TextEnd(int contentIndex, String contentSignature) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "text_end"; }
    }

    // ── Thinking content events ──────────────────────────────────────────

    /** Thinking content block start (Req 34.6). */
    record ThinkingStart(int contentIndex) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "thinking_start"; }
    }

    /** Thinking delta — no partial field (Req 34.2). */
    record ThinkingDelta(int contentIndex, String delta) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "thinking_delta"; }
    }

    /** Thinking content block end with optional contentSignature (Req 34.6, 34.7). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ThinkingEnd(int contentIndex, String contentSignature) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "thinking_end"; }
    }

    // ── Tool call events ─────────────────────────────────────────────────

    /** Tool call start with id and toolName (Req 34.8). */
    record ToolCallStart(int contentIndex, String id, String toolName) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "toolcall_start"; }
    }

    /** Tool call delta — no partial field (Req 34.2). */
    record ToolCallDelta(int contentIndex, String delta) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "toolcall_delta"; }
    }

    /** Tool call end. */
    record ToolCallEnd(int contentIndex) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "toolcall_end"; }
    }

    // ── Terminal events ──────────────────────────────────────────────────

    /**
     * Normal completion event (Req 34.4).
     *
     * @param reason stop reason (stop/length/toolUse)
     * @param usage  token usage statistics
     */
    record Done(StopReason reason, Usage usage) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "done"; }
    }

    /**
     * Error termination event (Req 34.5).
     *
     * @param reason       stop reason (aborted/error)
     * @param errorMessage optional error description
     * @param usage        token usage statistics
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Error(StopReason reason, String errorMessage, Usage usage) implements ProxyAssistantMessageEvent {
        @Override
        public String type() { return "error"; }
    }
}
