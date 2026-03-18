package com.pi.agent.loop;

import com.pi.agent.config.AgentLoopConfig;
import com.pi.agent.config.StreamFn;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentContext;
import com.pi.agent.types.AgentMessage;
import com.pi.ai.core.event.EventStream;
import com.pi.ai.core.types.CancellationSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Agent loop engine: provides {@link #agentLoop} and {@link #agentLoopContinue}
 * entry points for starting and continuing agent conversations.
 *
 * <p>This is a utility class with static methods only — no instantiation.
 *
 * <p><b>Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.5, 15.1, 15.2, 15.3, 15.4, 15.5</b>
 */
public final class AgentLoop {

    private AgentLoop() {
        // Utility class — no instantiation
    }

    /**
     * Start an agent loop with new prompt messages.
     *
     * <p>The prompts are appended to the context and events are emitted for each.
     * The returned {@link EventStream} emits {@link AgentEvent} instances and
     * completes with the list of all new messages produced during this run
     * (including prompts, assistant messages, and tool result messages).
     *
     * @param prompts  the prompt messages to send
     * @param context  the agent context (system prompt, messages, tools)
     * @param config   the agent loop configuration
     * @param signal   optional cancellation signal
     * @param streamFn optional streaming function (defaults to PiAi.streamSimple)
     * @return an event stream of agent events, completing with all new messages
     */
    public static EventStream<AgentEvent, List<AgentMessage>> agentLoop(
            List<AgentMessage> prompts,
            AgentContext context,
            AgentLoopConfig config,
            CancellationSignal signal,
            StreamFn streamFn) {

        EventStream<AgentEvent, List<AgentMessage>> stream = createAgentStream();

        CompletableFuture.runAsync(() -> {
            try {
                List<AgentMessage> messages = runAgentLoop(
                        prompts, context, config, stream, signal, streamFn);
                stream.end(messages);
            } catch (Exception e) {
                stream.end(List.of());
            }
        });

        return stream;
    }


    /**
     * Continue an agent loop from the current context without adding new messages.
     *
     * <p>Used for retries — the context already has a user message or tool results.
     * The last message in context must not be an assistant message.
     *
     * @param context  the agent context (must have non-empty messages, last message role ≠ assistant)
     * @param config   the agent loop configuration
     * @param signal   optional cancellation signal
     * @param streamFn optional streaming function (defaults to PiAi.streamSimple)
     * @return an event stream of agent events, completing with all new messages
     * @throws IllegalStateException if messages are empty or last message role is assistant
     */
    public static EventStream<AgentEvent, List<AgentMessage>> agentLoopContinue(
            AgentContext context,
            AgentLoopConfig config,
            CancellationSignal signal,
            StreamFn streamFn) {

        if (context.getMessages().isEmpty()) {
            throw new IllegalStateException("Cannot continue: no messages in context");
        }

        AgentMessage lastMessage = context.getMessages().get(context.getMessages().size() - 1);
        if ("assistant".equals(lastMessage.role())) {
            throw new IllegalStateException("Cannot continue from message role: assistant");
        }

        EventStream<AgentEvent, List<AgentMessage>> stream = createAgentStream();

        CompletableFuture.runAsync(() -> {
            try {
                List<AgentMessage> messages = runAgentLoopContinue(
                        context, config, stream, signal, streamFn);
                stream.end(messages);
            } catch (Exception e) {
                stream.end(List.of());
            }
        });

        return stream;
    }

    // ── Internal methods ─────────────────────────────────────────────────

    /**
     * Creates the agent event stream with completion detection on agent_end events.
     */
    static EventStream<AgentEvent, List<AgentMessage>> createAgentStream() {
        return new EventStream<>(
                event -> event instanceof AgentEvent.AgentEnd,
                event -> event instanceof AgentEvent.AgentEnd agentEnd
                        ? agentEnd.messages()
                        : List.of()
        );
    }

    /**
     * Internal: runs the agent loop for new prompts.
     * Appends prompts to context, emits lifecycle events, then delegates to runLoop.
     */
    static List<AgentMessage> runAgentLoop(
            List<AgentMessage> prompts,
            AgentContext context,
            AgentLoopConfig config,
            EventStream<AgentEvent, List<AgentMessage>> stream,
            CancellationSignal signal,
            StreamFn streamFn) {

        List<AgentMessage> newMessages = new ArrayList<>(prompts);

        // Append prompts to context messages
        for (AgentMessage prompt : prompts) {
            context.getMessages().add(prompt);
        }

        // Emit lifecycle events
        stream.push(new AgentEvent.AgentStart());
        stream.push(new AgentEvent.TurnStart());

        // Emit message_start/message_end for each prompt
        for (AgentMessage prompt : prompts) {
            stream.push(new AgentEvent.MessageStart(prompt));
            stream.push(new AgentEvent.MessageEnd(prompt));
        }

        // Delegate to runLoop (stub for now — Task 7.2 will implement the full logic)
        runLoop(context, newMessages, config, signal, stream, streamFn);

        return newMessages;
    }

    /**
     * Internal: runs the agent loop in continue mode (no new prompts).
     * Emits lifecycle events, then delegates to runLoop.
     */
    static List<AgentMessage> runAgentLoopContinue(
            AgentContext context,
            AgentLoopConfig config,
            EventStream<AgentEvent, List<AgentMessage>> stream,
            CancellationSignal signal,
            StreamFn streamFn) {

        List<AgentMessage> newMessages = new ArrayList<>();

        // Emit lifecycle events
        stream.push(new AgentEvent.AgentStart());
        stream.push(new AgentEvent.TurnStart());

        // Delegate to runLoop (stub for now — Task 7.2 will implement the full logic)
        runLoop(context, newMessages, config, signal, stream, streamFn);

        return newMessages;
    }

    /**
     * Main loop logic shared by agentLoop and agentLoopContinue.
     *
     * <p><b>Stub</b>: This method will be fully implemented in Task 7.2.
     * For now it emits agent_end with the current newMessages to complete the stream.
     */
    static void runLoop(
            AgentContext context,
            List<AgentMessage> newMessages,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream,
            StreamFn streamFn) {

        // Stub: emit turn_end and agent_end to properly close the event sequence.
        // Task 7.2 will replace this with the full double-loop logic.
        stream.push(new AgentEvent.TurnEnd(null, List.of()));
        stream.push(new AgentEvent.AgentEnd(newMessages));
    }
}
