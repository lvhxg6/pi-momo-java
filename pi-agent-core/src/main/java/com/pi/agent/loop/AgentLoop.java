package com.pi.agent.loop;

import com.pi.agent.config.AgentLoopConfig;
import com.pi.agent.config.ConvertToLlmFunction;
import com.pi.agent.config.StreamFn;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentContext;
import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.event.AssistantMessageEvent;
import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.event.EventStream;
import com.pi.ai.core.stream.PiAi;
import com.pi.ai.core.types.AssistantContentBlock;
import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.CancellationSignal;
import com.pi.ai.core.types.Context;
import com.pi.ai.core.types.Message;
import com.pi.ai.core.types.SimpleStreamOptions;
import com.pi.ai.core.types.StopReason;
import com.pi.ai.core.types.Tool;
import com.pi.ai.core.types.ToolCall;
import com.pi.ai.core.types.ToolResultMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
     * <p>Executes the following pipeline:
     * <ol>
     *   <li>transformContext (optional) — transforms AgentMessage list</li>
     *   <li>convertToLlm — converts AgentMessage list to LLM Message list</li>
     *   <li>Build LLM Context with systemPrompt, messages, and tools</li>
     *   <li>Resolve API key (optional via getApiKey callback)</li>
     *   <li>Call StreamFn (defaults to PiAi::streamSimple)</li>
     *   <li>Process event stream: start/delta/done/error</li>
     * </ol>
     *
     * <p><b>Validates: Requirements 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 17.8, 17.9</b>
     */
    static AgentMessage streamAssistantResponse(
            AgentContext context,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream,
            StreamFn streamFn) {

        // 1. Apply context transform if configured (Req 17.1, 17.2)
        List<AgentMessage> messages = context.getMessages();
        if (config.getTransformContext() != null) {
            try {
                messages = config.getTransformContext().transform(messages, signal).join();
            } catch (Exception e) {
                // transformContext should not throw; fall back to original messages
                messages = context.getMessages();
            }
        }

        // 2. Convert to LLM messages (Req 17.3)
        ConvertToLlmFunction convertFn = config.getConvertToLlm();
        List<Message> llmMessages;
        if (convertFn != null) {
            llmMessages = convertFn.convert(messages);
        } else {
            // Default: filter MessageAdapter instances and unwrap them
            llmMessages = messages.stream()
                    .filter(MessageAdapter::isLlmMessage)
                    .map(MessageAdapter::unwrap)
                    .collect(Collectors.toList());
        }

        // 3. Build LLM Context (Req 17.4)
        List<Tool> tools = null;
        if (context.getTools() != null && !context.getTools().isEmpty()) {
            tools = context.getTools().stream()
                    .map(AgentTool::toTool)
                    .collect(Collectors.toList());
        }
        Context llmContext = new Context(context.getSystemPrompt(), llmMessages, tools);

        // 4. Resolve API key (Req 17.5)
        SimpleStreamOptions baseOptions = config.getStreamOptions();
        String resolvedApiKey = baseOptions.getApiKey();
        if (config.getGetApiKey() != null && config.getModel() != null) {
            try {
                String dynamicKey = config.getGetApiKey().getApiKey(config.getModel().provider()).join();
                if (dynamicKey != null) {
                    resolvedApiKey = dynamicKey;
                }
            } catch (Exception e) {
                // Fall back to static apiKey
            }
        }

        // Build stream options with resolved apiKey and signal
        SimpleStreamOptions effectiveOptions = SimpleStreamOptions.simpleBuilder()
                .temperature(baseOptions.getTemperature())
                .maxTokens(baseOptions.getMaxTokens())
                .apiKey(resolvedApiKey)
                .cacheRetention(baseOptions.getCacheRetention())
                .sessionId(baseOptions.getSessionId())
                .headers(baseOptions.getHeaders())
                .transport(baseOptions.getTransport())
                .maxRetryDelayMs(baseOptions.getMaxRetryDelayMs())
                .metadata(baseOptions.getMetadata())
                .onPayload(baseOptions.getOnPayload())
                .signal(signal != null ? signal : baseOptions.getSignal())
                .reasoning(baseOptions.getReasoning())
                .thinkingBudgets(baseOptions.getThinkingBudgets())
                .build();

        // 5. Call StreamFn (Req 12.2)
        StreamFn fn = streamFn != null ? streamFn : PiAi::streamSimple;
        AssistantMessageEventStream response = fn.stream(config.getModel(), llmContext, effectiveOptions);

        // 6. Process event stream (Req 17.6, 17.7, 17.8, 17.9)
        AgentMessage partialAgentMsg = null;
        boolean addedPartial = false;

        for (AssistantMessageEvent event : response) {
            if (event instanceof AssistantMessageEvent.Start) {
                AssistantMessageEvent.Start start = (AssistantMessageEvent.Start) event;
                // Req 17.6: wrap partial, add to context, emit message_start
                partialAgentMsg = MessageAdapter.wrap(start.partial());
                context.getMessages().add(partialAgentMsg);
                addedPartial = true;
                stream.push(new AgentEvent.MessageStart(partialAgentMsg));

            } else if (event instanceof AssistantMessageEvent.Done) {
                // Req 17.8: replace partial with final message
                AssistantMessage finalMsg = response.result().join();
                AgentMessage finalAgentMsg = MessageAdapter.wrap(finalMsg);
                if (addedPartial) {
                    replaceLastMessage(context, finalAgentMsg);
                } else {
                    context.getMessages().add(finalAgentMsg);
                    stream.push(new AgentEvent.MessageStart(finalAgentMsg));
                }
                stream.push(new AgentEvent.MessageEnd(finalAgentMsg));
                return finalAgentMsg;

            } else if (event instanceof AssistantMessageEvent.Error) {
                // Req 17.8: same handling as done
                AssistantMessage finalMsg = response.result().join();
                AgentMessage finalAgentMsg = MessageAdapter.wrap(finalMsg);
                if (addedPartial) {
                    replaceLastMessage(context, finalAgentMsg);
                } else {
                    context.getMessages().add(finalAgentMsg);
                    stream.push(new AgentEvent.MessageStart(finalAgentMsg));
                }
                stream.push(new AgentEvent.MessageEnd(finalAgentMsg));
                return finalAgentMsg;

            } else {
                // Req 17.7: all delta events — update context.messages and emit message_update
                if (partialAgentMsg != null) {
                    AssistantMessage partial = extractPartialFromEvent(event);
                    if (partial != null) {
                        partialAgentMsg = MessageAdapter.wrap(partial);
                        replaceLastMessage(context, partialAgentMsg);
                    }
                    stream.push(new AgentEvent.MessageUpdate(partialAgentMsg, event));
                }
            }
        }

        // Defensive: stream ended without done/error event
        AssistantMessage finalMsg = response.result().join();
        AgentMessage finalAgentMsg = MessageAdapter.wrap(finalMsg);
        if (addedPartial) {
            replaceLastMessage(context, finalAgentMsg);
        } else {
            context.getMessages().add(finalAgentMsg);
            stream.push(new AgentEvent.MessageStart(finalAgentMsg));
        }
        stream.push(new AgentEvent.MessageEnd(finalAgentMsg));
        return finalAgentMsg;
    }

    /**
     * Replaces the last message in the context's message list.
     */
    private static void replaceLastMessage(AgentContext context, AgentMessage message) {
        List<AgentMessage> messages = context.getMessages();
        if (!messages.isEmpty()) {
            messages.set(messages.size() - 1, message);
        }
    }

    /**
     * Extracts the partial {@link AssistantMessage} from a delta event.
     * Returns {@code null} if the event type is not recognized as a delta event.
     *
     * <p>Uses if-else instanceof chains for Java 17 compatibility.
     */
    private static AssistantMessage extractPartialFromEvent(AssistantMessageEvent event) {
        if (event instanceof AssistantMessageEvent.TextStart e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.TextDelta e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.TextEnd e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.ThinkingStart e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.ThinkingDelta e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.ThinkingEnd e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.ToolCallStart e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.ToolCallDelta e) {
            return e.partial();
        } else if (event instanceof AssistantMessageEvent.ToolCallEnd e) {
            return e.partial();
        }
        return null;
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
