package com.pi.agent.loop;

import com.pi.agent.config.AgentLoopConfig;
import com.pi.agent.config.StreamFn;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentContext;
import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.event.EventStream;
import com.pi.ai.core.types.AssistantContentBlock;
import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.CancellationSignal;
import com.pi.ai.core.types.StopReason;
import com.pi.ai.core.types.ToolCall;
import com.pi.ai.core.types.ToolResultMessage;

import java.util.ArrayList;
import java.util.Collections;
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
     * <p>Implements a double-loop structure:
     * <ul>
     *   <li>Outer loop: handles FollowUp messages — continues when queued follow-up
     *       messages arrive after the agent would otherwise stop.</li>
     *   <li>Inner loop: handles tool calls and Steering messages — continues as long
     *       as there are pending tool calls or injected steering messages.</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7, 16.8, 16.9, 16.10</b>
     */
    static void runLoop(
            AgentContext context,
            List<AgentMessage> newMessages,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream,
            StreamFn streamFn) {

        boolean firstTurn = true;

        // Check for steering messages at start (user may have typed while waiting)
        List<AgentMessage> pendingMessages = pollSteeringMessages(config);

        // Outer loop: continues when queued follow-up messages arrive after agent would stop
        while (true) {
            boolean hasMoreToolCalls = true;

            // Inner loop: process tool calls and steering messages
            while (hasMoreToolCalls || !pendingMessages.isEmpty()) {

                // Emit turn_start for non-first turns (first turn_start is emitted by the caller)
                if (!firstTurn) {
                    stream.push(new AgentEvent.TurnStart());
                } else {
                    firstTurn = false;
                }

                // Process pending messages (inject before next assistant response)
                if (!pendingMessages.isEmpty()) {
                    for (AgentMessage msg : pendingMessages) {
                        stream.push(new AgentEvent.MessageStart(msg));
                        stream.push(new AgentEvent.MessageEnd(msg));
                        context.getMessages().add(msg);
                        newMessages.add(msg);
                    }
                    pendingMessages = new ArrayList<>();
                }

                // Stream assistant response (stub — Task 7.3 will implement the full logic)
                AgentMessage assistantAgentMsg = streamAssistantResponse(
                        context, config, signal, stream, streamFn);
                newMessages.add(assistantAgentMsg);

                // Check stopReason for error/aborted — terminate immediately
                StopReason stopReason = extractStopReason(assistantAgentMsg);
                if (stopReason == StopReason.ERROR || stopReason == StopReason.ABORTED) {
                    stream.push(new AgentEvent.TurnEnd(assistantAgentMsg, List.of()));
                    stream.push(new AgentEvent.AgentEnd(newMessages));
                    return;
                }

                // Check for tool calls in the assistant message
                List<ToolCall> toolCalls = extractToolCalls(assistantAgentMsg);
                hasMoreToolCalls = !toolCalls.isEmpty();

                List<ToolResultMessage> toolResults = new ArrayList<>();
                if (hasMoreToolCalls) {
                    // Execute tool calls (stub — Tasks 7.4-7.8 will implement)
                    toolResults.addAll(executeToolCalls(
                            context, assistantAgentMsg, config, signal, stream));

                    // Append tool results to context and newMessages
                    for (ToolResultMessage result : toolResults) {
                        AgentMessage wrappedResult = MessageAdapter.wrap(result);
                        context.getMessages().add(wrappedResult);
                        newMessages.add(wrappedResult);
                    }
                }

                // Emit turn_end with the assistant message and tool results
                stream.push(new AgentEvent.TurnEnd(assistantAgentMsg, toolResults));

                // Poll for steering messages after each turn
                pendingMessages = pollSteeringMessages(config);
            }

            // Agent would stop here. Check for follow-up messages.
            List<AgentMessage> followUpMessages = pollFollowUpMessages(config);
            if (!followUpMessages.isEmpty()) {
                // Set as pending so inner loop processes them
                pendingMessages = followUpMessages;
                continue;
            }

            // No more messages, exit outer loop
            break;
        }

        // Normal completion — emit agent_end
        stream.push(new AgentEvent.AgentEnd(newMessages));
    }

    // ── Stub methods (to be implemented in subsequent tasks) ─────────────

    /**
     * Streams an assistant response from the LLM.
     *
     * <p><b>Stub</b>: Task 7.3 will implement the full logic including
     * transformContext → convertToLlm → build LLM Context → call StreamFn.
     * For now, returns a minimal AssistantMessage with stopReason=STOP and
     * emits message_start/message_end events.
     */
    static AgentMessage streamAssistantResponse(
            AgentContext context,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream,
            StreamFn streamFn) {

        // Create a minimal stub AssistantMessage with no tool calls
        AssistantMessage stubMsg = AssistantMessage.builder()
                .content(List.of())
                .stopReason(StopReason.STOP)
                .timestamp(System.currentTimeMillis())
                .build();

        AgentMessage agentMsg = MessageAdapter.wrap(stubMsg);

        // Add to context and emit events
        context.getMessages().add(agentMsg);
        stream.push(new AgentEvent.MessageStart(agentMsg));
        stream.push(new AgentEvent.MessageEnd(agentMsg));

        return agentMsg;
    }

    /**
     * Executes tool calls from an assistant message.
     *
     * <p><b>Stub</b>: Tasks 7.4-7.8 will implement the full logic including
     * prepareToolCall, executePreparedToolCall, finalizeExecutedToolCall,
     * and sequential/parallel execution modes.
     * For now, returns an empty list (no tool results).
     */
    static List<ToolResultMessage> executeToolCalls(
            AgentContext context,
            AgentMessage assistantMessage,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream) {

        // Stub: return empty results. Tasks 7.4-7.8 will implement full tool execution.
        return List.of();
    }

    // ── Helper methods ───────────────────────────────────────────────────

    /**
     * Polls steering messages from the config callback.
     *
     * @return list of steering messages, or empty list if callback is null or returns null
     */
    private static List<AgentMessage> pollSteeringMessages(AgentLoopConfig config) {
        if (config.getGetSteeringMessages() == null) {
            return new ArrayList<>();
        }
        try {
            List<AgentMessage> messages = config.getGetSteeringMessages().get().join();
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Polls follow-up messages from the config callback.
     *
     * @return list of follow-up messages, or empty list if callback is null or returns null
     */
    private static List<AgentMessage> pollFollowUpMessages(AgentLoopConfig config) {
        if (config.getGetFollowUpMessages() == null) {
            return new ArrayList<>();
        }
        try {
            List<AgentMessage> messages = config.getGetFollowUpMessages().get().join();
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Extracts the {@link StopReason} from an {@link AgentMessage}.
     * Returns {@code null} if the message is not an AssistantMessage wrapper.
     */
    private static StopReason extractStopReason(AgentMessage agentMsg) {
        if (agentMsg instanceof MessageAdapter adapter
                && adapter.message() instanceof AssistantMessage assistantMsg) {
            return assistantMsg.getStopReason();
        }
        return null;
    }

    /**
     * Extracts {@link ToolCall} content blocks from an {@link AgentMessage}.
     * Returns an empty list if the message is not an AssistantMessage wrapper
     * or contains no tool calls.
     */
    private static List<ToolCall> extractToolCalls(AgentMessage agentMsg) {
        if (agentMsg instanceof MessageAdapter adapter
                && adapter.message() instanceof AssistantMessage assistantMsg) {
            if (assistantMsg.getContent() == null) {
                return Collections.emptyList();
            }
            List<ToolCall> toolCalls = new ArrayList<>();
            for (AssistantContentBlock block : assistantMsg.getContent()) {
                if (block instanceof ToolCall tc) {
                    toolCalls.add(tc);
                }
            }
            return toolCalls;
        }
        return Collections.emptyList();
    }
}
