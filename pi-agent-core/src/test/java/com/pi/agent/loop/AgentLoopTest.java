package com.pi.agent.loop;

import com.pi.agent.config.AgentLoopConfig;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentContext;
import com.pi.agent.types.AgentMessage;
import com.pi.ai.core.event.EventStream;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AgentLoop} entry functions.
 *
 * <p><b>Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.5, 15.1, 15.2, 15.3, 15.4, 15.5</b>
 */
class AgentLoopTest {

    // ── Helpers ──────────────────────────────────────────────────────────

    private static AgentMessage stubMessage(String role) {
        return new AgentMessage() {
            @Override public String role() { return role; }
            @Override public long timestamp() { return System.currentTimeMillis(); }
            @Override public String toString() { return "StubMessage[" + role + "]"; }
        };
    }

    private static AgentContext emptyContext() {
        return AgentContext.builder()
                .systemPrompt("test")
                .build();
    }

    private static AgentLoopConfig minimalConfig() {
        return AgentLoopConfig.builder().build();
    }

    /**
     * Collects all events from the stream into a list.
     */
    private static List<AgentEvent> collectEvents(EventStream<AgentEvent, List<AgentMessage>> stream) {
        List<AgentEvent> events = new ArrayList<>();
        for (AgentEvent event : stream) {
            events.add(event);
        }
        return events;
    }

    // ==================== agentLoop: basic behavior ====================

    @Test
    void agentLoop_returnsEventStream() {
        AgentContext ctx = emptyContext();
        List<AgentMessage> prompts = List.of(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(prompts, ctx, minimalConfig(), null, null);

        assertThat(stream).isNotNull();
        List<AgentEvent> events = collectEvents(stream);
        assertThat(events).isNotEmpty();
    }

    @Test
    void agentLoop_appendsPromptsToContext() {
        AgentContext ctx = emptyContext();
        AgentMessage prompt = stubMessage("user");

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(List.of(prompt), ctx, minimalConfig(), null, null);

        // Consume all events to let the async loop complete
        collectEvents(stream);

        // Prompts should have been appended to context.messages
        assertThat(ctx.getMessages()).contains(prompt);
    }

    @Test
    void agentLoop_emitsAgentStartFirst() {
        AgentContext ctx = emptyContext();
        List<AgentMessage> prompts = List.of(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(prompts, ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);
        assertThat(events.get(0)).isInstanceOf(AgentEvent.AgentStart.class);
    }

    @Test
    void agentLoop_emitsTurnStartAfterAgentStart() {
        AgentContext ctx = emptyContext();
        List<AgentMessage> prompts = List.of(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(prompts, ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);
        assertThat(events.get(0)).isInstanceOf(AgentEvent.AgentStart.class);
        assertThat(events.get(1)).isInstanceOf(AgentEvent.TurnStart.class);
    }

    @Test
    void agentLoop_emitsMessageStartAndEndForEachPrompt() {
        AgentContext ctx = emptyContext();
        AgentMessage p1 = stubMessage("user");
        AgentMessage p2 = stubMessage("user");

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(List.of(p1, p2), ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);

        // After agent_start and turn_start, we expect message_start/end pairs
        assertThat(events.get(2)).isInstanceOf(AgentEvent.MessageStart.class);
        assertThat(((AgentEvent.MessageStart) events.get(2)).message()).isSameAs(p1);
        assertThat(events.get(3)).isInstanceOf(AgentEvent.MessageEnd.class);
        assertThat(((AgentEvent.MessageEnd) events.get(3)).message()).isSameAs(p1);

        assertThat(events.get(4)).isInstanceOf(AgentEvent.MessageStart.class);
        assertThat(((AgentEvent.MessageStart) events.get(4)).message()).isSameAs(p2);
        assertThat(events.get(5)).isInstanceOf(AgentEvent.MessageEnd.class);
        assertThat(((AgentEvent.MessageEnd) events.get(5)).message()).isSameAs(p2);
    }


    @Test
    void agentLoop_eventSequenceEndsWithAgentEnd() {
        AgentContext ctx = emptyContext();
        List<AgentMessage> prompts = List.of(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(prompts, ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent).isInstanceOf(AgentEvent.AgentEnd.class);
    }

    @Test
    void agentLoop_resultContainsPrompts() {
        AgentContext ctx = emptyContext();
        AgentMessage prompt = stubMessage("user");

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(List.of(prompt), ctx, minimalConfig(), null, null);

        // Consume events to let the loop complete
        collectEvents(stream);

        List<AgentMessage> result = stream.result().join();
        assertThat(result).contains(prompt);
    }

    @Test
    void agentLoop_multiplePrompts_allAppendedToContext() {
        AgentContext ctx = emptyContext();
        AgentMessage p1 = stubMessage("user");
        AgentMessage p2 = stubMessage("user");

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(List.of(p1, p2), ctx, minimalConfig(), null, null);

        collectEvents(stream);

        assertThat(ctx.getMessages()).contains(p1, p2);
    }

    @Test
    void agentLoop_acceptsNullSignalAndStreamFn() {
        AgentContext ctx = emptyContext();
        List<AgentMessage> prompts = List.of(stubMessage("user"));

        // Should not throw
        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoop(prompts, ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);
        assertThat(events).isNotEmpty();
    }

    // ==================== agentLoopContinue: precondition checks ====================

    @Test
    void agentLoopContinue_emptyMessages_throwsIllegalState() {
        AgentContext ctx = emptyContext();

        assertThatThrownBy(() ->
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot continue: no messages in context");
    }

    @Test
    void agentLoopContinue_lastMessageIsAssistant_throwsIllegalState() {
        AgentContext ctx = emptyContext();
        ctx.getMessages().add(stubMessage("assistant"));

        assertThatThrownBy(() ->
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot continue from message role: assistant");
    }

    // ==================== agentLoopContinue: valid usage ====================

    @Test
    void agentLoopContinue_validContext_returnsEventStream() {
        AgentContext ctx = emptyContext();
        ctx.getMessages().add(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null);

        assertThat(stream).isNotNull();
        List<AgentEvent> events = collectEvents(stream);
        assertThat(events).isNotEmpty();
    }

    @Test
    void agentLoopContinue_emitsAgentStartAndTurnStart() {
        AgentContext ctx = emptyContext();
        ctx.getMessages().add(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);
        assertThat(events.get(0)).isInstanceOf(AgentEvent.AgentStart.class);
        assertThat(events.get(1)).isInstanceOf(AgentEvent.TurnStart.class);
    }

    @Test
    void agentLoopContinue_doesNotEmitMessageStartEndForExistingMessages() {
        AgentContext ctx = emptyContext();
        ctx.getMessages().add(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);

        // After agent_start and turn_start, there should be no message_start/end
        // for the existing user message. However, the runLoop will produce a stub
        // assistant message which does emit message_start/end.
        long messageStartCount = events.stream()
                .filter(e -> e instanceof AgentEvent.MessageStart ms
                        && "user".equals(ms.message().role()))
                .count();
        assertThat(messageStartCount).isZero();
    }

    @Test
    void agentLoopContinue_endsWithAgentEnd() {
        AgentContext ctx = emptyContext();
        ctx.getMessages().add(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent).isInstanceOf(AgentEvent.AgentEnd.class);
    }

    @Test
    void agentLoopContinue_resultContainsAssistantMessage() {
        AgentContext ctx = emptyContext();
        ctx.getMessages().add(stubMessage("user"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null);

        collectEvents(stream);

        // In continue mode, runLoop produces a stub assistant message
        List<AgentMessage> result = stream.result().join();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("assistant");
    }

    @Test
    void agentLoopContinue_lastMessageIsToolResult_succeeds() {
        AgentContext ctx = emptyContext();
        ctx.getMessages().add(stubMessage("toolResult"));

        EventStream<AgentEvent, List<AgentMessage>> stream =
                AgentLoop.agentLoopContinue(ctx, minimalConfig(), null, null);

        List<AgentEvent> events = collectEvents(stream);
        assertThat(events).isNotEmpty();
    }

    // ==================== createAgentStream: internal helper ====================

    @Test
    void createAgentStream_completesOnAgentEnd() {
        EventStream<AgentEvent, List<AgentMessage>> stream = AgentLoop.createAgentStream();

        AgentMessage msg = stubMessage("user");
        List<AgentMessage> messages = List.of(msg);

        stream.push(new AgentEvent.AgentStart());
        stream.push(new AgentEvent.AgentEnd(messages));

        List<AgentMessage> result = stream.result().join();
        assertThat(result).containsExactly(msg);
    }

    @Test
    void createAgentStream_nonAgentEndDoesNotComplete() {
        EventStream<AgentEvent, List<AgentMessage>> stream = AgentLoop.createAgentStream();

        stream.push(new AgentEvent.AgentStart());
        stream.push(new AgentEvent.TurnStart());

        assertThat(stream.result().isDone()).isFalse();

        // Clean up
        stream.end(List.of());
    }
}
