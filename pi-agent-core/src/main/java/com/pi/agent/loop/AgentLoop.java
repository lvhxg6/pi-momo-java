package com.pi.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.pi.agent.config.AgentLoopConfig;
import com.pi.agent.config.BeforeToolCallHook;
import com.pi.agent.config.ConvertToLlmFunction;
import com.pi.agent.config.StreamFn;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentContext;
import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.AgentToolResult;
import com.pi.agent.types.AgentToolUpdateCallback;
import com.pi.agent.types.BeforeToolCallContext;
import com.pi.agent.types.BeforeToolCallResult;
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
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.SimpleStreamOptions;
import com.pi.ai.core.types.StopReason;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.Tool;
import com.pi.ai.core.types.ToolCall;
import com.pi.ai.core.types.ToolResultMessage;
import com.pi.ai.core.types.Usage;
import com.pi.ai.core.util.PiAiJson;
import com.pi.ai.core.util.ToolValidator;

import com.pi.agent.config.AfterToolCallHook;
import com.pi.agent.types.AfterToolCallContext;
import com.pi.agent.types.AfterToolCallResult;
import com.pi.agent.types.ToolExecutionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
                // Create error AssistantMessage and emit AgentEnd with error info
                Model model = config.getModel();
                AssistantMessage errorMsg = AssistantMessage.builder()
                        .content(List.of(new TextContent("")))
                        .api(model != null ? model.api() : null)
                        .provider(model != null ? model.provider() : null)
                        .model(model != null ? model.id() : null)
                        .usage(new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)))
                        .stopReason(StopReason.ERROR)
                        .errorMessage(e.getMessage())
                        .timestamp(System.currentTimeMillis())
                        .build();
                AgentMessage wrappedErrorMsg = MessageAdapter.wrap(errorMsg);
                stream.push(new AgentEvent.MessageEnd(wrappedErrorMsg));
                stream.push(new AgentEvent.AgentEnd(List.of(wrappedErrorMsg)));
                stream.end(List.of(wrappedErrorMsg));
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
     * <p>Extracts tool calls from the assistant message and dispatches to either
     * {@link #executeToolCallsSequential} or {@link #executeToolCallsParallel}
     * based on the configured {@link ToolExecutionMode}.
     *
     * <p><b>Validates: Requirements 18.1, 18.2, 18.3, 18.4, 19.1, 19.2, 19.3, 19.4, 19.5</b>
     */
    static List<ToolResultMessage> executeToolCalls(
            AgentContext context,
            AgentMessage assistantMessage,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream) {

        // Extract tool calls from assistant message
        List<ToolCall> toolCalls = extractToolCalls(assistantMessage);
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        // Dispatch based on tool execution mode (default is PARALLEL)
        ToolExecutionMode mode = config.getToolExecution();
        if (mode == ToolExecutionMode.SEQUENTIAL) {
            return executeToolCallsSequential(context, assistantMessage, toolCalls, config, signal, stream);
        } else {
            return executeToolCallsParallel(context, assistantMessage, toolCalls, config, signal, stream);
        }
    }

    /**
     * Executes tool calls sequentially: prepare → execute → finalize for each tool call.
     *
     * <p>For each tool call:
     * <ol>
     *   <li>Emit {@code tool_execution_start}</li>
     *   <li>Prepare the tool call</li>
     *   <li>If Immediate: emit events and add result directly</li>
     *   <li>If Prepared: execute, then finalize</li>
     * </ol>
     *
     * <p><b>Validates: Requirements 18.1, 18.2, 18.3, 18.4</b>
     */
    static List<ToolResultMessage> executeToolCallsSequential(
            AgentContext context,
            AgentMessage assistantMessage,
            List<ToolCall> toolCalls,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream) {

        List<ToolResultMessage> results = new ArrayList<>();

        for (ToolCall toolCall : toolCalls) {
            // Emit tool_execution_start (Req 18.1)
            stream.push(new AgentEvent.ToolExecutionStart(
                    toolCall.id(), toolCall.name(), toolCall.arguments()));

            // Prepare the tool call (Req 18.2)
            PrepareResult preparation = prepareToolCall(context, assistantMessage, toolCall, config, signal);

            if (preparation instanceof PrepareResult.Immediate immediate) {
                // Immediate result: emit events and add result directly (Req 18.3)
                stream.push(new AgentEvent.ToolExecutionEnd(
                        toolCall.id(), toolCall.name(), immediate.result(), immediate.isError()));

                ToolResultMessage toolResultMsg = buildToolResultMessage(
                        toolCall.id(), toolCall.name(), immediate.result(), immediate.isError());
                stream.push(new AgentEvent.MessageStart(MessageAdapter.wrap(toolResultMsg)));
                stream.push(new AgentEvent.MessageEnd(MessageAdapter.wrap(toolResultMsg)));
                results.add(toolResultMsg);

            } else if (preparation instanceof PrepareResult.Prepared prepared) {
                // Execute and finalize (Req 18.4)
                ExecuteResult executed = executePreparedToolCall(prepared, signal, stream);
                ToolResultMessage toolResultMsg = finalizeExecutedToolCall(
                        context, assistantMessage, prepared, executed, config, signal, stream);
                results.add(toolResultMsg);
            }
        }

        return results;
    }

    /**
     * Executes tool calls in parallel with three phases:
     * <ol>
     *   <li>Phase 1 (prepare): Sequentially prepare each tool call, emit {@code tool_execution_start}</li>
     *   <li>Phase 2 (execute): Concurrently execute all Prepared tools using {@code CompletableFuture.supplyAsync}</li>
     *   <li>Phase 3 (finalize): Join and finalize in original tool call order</li>
     * </ol>
     *
     * <p><b>Validates: Requirements 19.1, 19.2, 19.3, 19.4, 19.5</b>
     */
    static List<ToolResultMessage> executeToolCallsParallel(
            AgentContext context,
            AgentMessage assistantMessage,
            List<ToolCall> toolCalls,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream) {

        // Phase 1: Sequential prepare (Req 19.1)
        List<PrepareResultEntry> preparedCalls = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            // Emit tool_execution_start
            stream.push(new AgentEvent.ToolExecutionStart(
                    toolCall.id(), toolCall.name(), toolCall.arguments()));

            PrepareResult preparation = prepareToolCall(context, assistantMessage, toolCall, config, signal);
            preparedCalls.add(new PrepareResultEntry(toolCall, preparation));
        }

        // Phase 2: Parallel execute (Req 19.2, 19.3)
        List<RunningCallEntry> runningCalls = new ArrayList<>();
        for (PrepareResultEntry entry : preparedCalls) {
            if (entry.preparation() instanceof PrepareResult.Prepared prepared) {
                CompletableFuture<ExecuteResult> future = CompletableFuture.supplyAsync(() ->
                        executePreparedToolCall(prepared, signal, stream));
                runningCalls.add(new RunningCallEntry(entry.toolCall(), prepared, future));
            }
        }

        // Phase 3: Finalize in original order (Req 19.4, 19.5)
        List<ToolResultMessage> results = new ArrayList<>();
        int runningIndex = 0;

        for (PrepareResultEntry entry : preparedCalls) {
            if (entry.preparation() instanceof PrepareResult.Immediate immediate) {
                // Handle immediate result
                stream.push(new AgentEvent.ToolExecutionEnd(
                        entry.toolCall().id(), entry.toolCall().name(),
                        immediate.result(), immediate.isError()));

                ToolResultMessage toolResultMsg = buildToolResultMessage(
                        entry.toolCall().id(), entry.toolCall().name(),
                        immediate.result(), immediate.isError());
                stream.push(new AgentEvent.MessageStart(MessageAdapter.wrap(toolResultMsg)));
                stream.push(new AgentEvent.MessageEnd(MessageAdapter.wrap(toolResultMsg)));
                results.add(toolResultMsg);

            } else if (entry.preparation() instanceof PrepareResult.Prepared) {
                // Find the corresponding running call and join
                RunningCallEntry running = runningCalls.get(runningIndex++);
                ExecuteResult executed = running.future().join();
                ToolResultMessage toolResultMsg = finalizeExecutedToolCall(
                        context, assistantMessage, running.prepared(), executed, config, signal, stream);
                results.add(toolResultMsg);
            }
        }

        return results;
    }

    /**
     * Entry for tracking prepare results with their original tool calls.
     */
    private record PrepareResultEntry(ToolCall toolCall, PrepareResult preparation) {}

    /**
     * Entry for tracking running tool executions.
     */
    private record RunningCallEntry(ToolCall toolCall, PrepareResult.Prepared prepared,
                                    CompletableFuture<ExecuteResult> future) {}

    /**
     * Finalizes an executed tool call by applying AfterToolCallHook and emitting events.
     *
     * <p>Steps:
     * <ol>
     *   <li>Call AfterToolCallHook if configured (field-level merge)</li>
     *   <li>Emit {@code tool_execution_end}</li>
     *   <li>Build {@link ToolResultMessage}</li>
     *   <li>Emit {@code message_start} and {@code message_end}</li>
     * </ol>
     *
     * <p><b>Validates: Requirements 22.1, 22.2, 22.3, 22.4, 22.5</b>
     */
    static ToolResultMessage finalizeExecutedToolCall(
            AgentContext context,
            AgentMessage assistantMsg,
            PrepareResult.Prepared prepared,
            ExecuteResult executed,
            AgentLoopConfig config,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream) {

        AgentToolResult<?> result = executed.result();
        boolean isError = executed.isError();

        // Call AfterToolCallHook if configured (Req 22.1, 22.2)
        AfterToolCallHook afterHook = config.getAfterToolCall();
        if (afterHook != null) {
            // Extract AssistantMessage from AgentMessage via MessageAdapter
            AssistantMessage assistantMessage = null;
            if (assistantMsg instanceof MessageAdapter adapter
                    && adapter.message() instanceof AssistantMessage am) {
                assistantMessage = am;
            }

            if (assistantMessage != null) {
                AfterToolCallContext afterContext = new AfterToolCallContext(
                        assistantMessage,
                        prepared.toolCall(),
                        prepared.args(),
                        result,
                        isError,
                        context);

                try {
                    AfterToolCallResult afterResult = afterHook.call(afterContext, signal).join();

                    // Field-level merge: non-null fields override (Req 22.2)
                    if (afterResult != null) {
                        if (afterResult.content() != null) {
                            result = new AgentToolResult<>(afterResult.content(), result.details());
                        }
                        if (afterResult.details() != null) {
                            result = new AgentToolResult<>(result.content(), afterResult.details());
                        }
                        if (afterResult.isError() != null) {
                            isError = afterResult.isError();
                        }
                    }
                } catch (Exception e) {
                    // AfterToolCallHook failure: keep original result
                }
            }
        }

        // Emit tool_execution_end (Req 22.3)
        stream.push(new AgentEvent.ToolExecutionEnd(
                prepared.toolCall().id(), prepared.tool().name(), result, isError));

        // Build ToolResultMessage (Req 22.4)
        ToolResultMessage toolResultMessage = buildToolResultMessage(
                prepared.toolCall().id(), prepared.tool().name(), result, isError);

        // Emit message_start/message_end (Req 22.5)
        AgentMessage wrappedResult = MessageAdapter.wrap(toolResultMessage);
        stream.push(new AgentEvent.MessageStart(wrappedResult));
        stream.push(new AgentEvent.MessageEnd(wrappedResult));

        return toolResultMessage;
    }

    /**
     * Builds a {@link ToolResultMessage} from tool execution result.
     */
    private static ToolResultMessage buildToolResultMessage(
            String toolCallId,
            String toolName,
            AgentToolResult<?> result,
            boolean isError) {
        return new ToolResultMessage(
                toolCallId,
                toolName,
                result.content(),
                result.details(),
                isError,
                System.currentTimeMillis());
    }

    // ── Tool preparation ─────────────────────────────────────────────────

    /**
     * Prepares a tool call for execution by performing all pre-execution checks:
     * <ol>
     *   <li>Find the tool by name in the context's tools list</li>
     *   <li>Validate arguments using {@link ToolValidator#validateToolArguments}</li>
     *   <li>Call {@link BeforeToolCallHook} if configured</li>
     * </ol>
     *
     * <p>Returns {@link PrepareResult.Prepared} if all checks pass, or
     * {@link PrepareResult.Immediate} with an error result if any check fails.
     *
     * <p><b>Validates: Requirements 20.1, 20.2, 20.3, 20.4, 20.5, 20.6, 20.7</b>
     *
     * @param context      the current agent context
     * @param assistantMsg the assistant message that requested the tool call
     * @param toolCall     the tool call to prepare
     * @param config       the agent loop configuration
     * @param signal       optional cancellation signal
     * @return a {@link PrepareResult} indicating whether the tool is ready or was rejected
     */
    static PrepareResult prepareToolCall(
            AgentContext context,
            AgentMessage assistantMsg,
            ToolCall toolCall,
            AgentLoopConfig config,
            CancellationSignal signal) {

        // 1. Find tool by name (Req 20.1, 20.2)
        AgentTool tool = null;
        if (context.getTools() != null) {
            for (AgentTool t : context.getTools()) {
                if (t.name().equals(toolCall.name())) {
                    tool = t;
                    break;
                }
            }
        }
        if (tool == null) {
            return new PrepareResult.Immediate(
                    createErrorToolResult("Tool " + toolCall.name() + " not found"), true);
        }

        // 2. Validate arguments using ToolValidator (Req 20.3, 20.4)
        try {
            ToolValidator.validateToolArguments(tool.toTool(), toolCall);
        } catch (IllegalArgumentException e) {
            return new PrepareResult.Immediate(
                    createErrorToolResult(e.getMessage()), true);
        }

        // 3. Call BeforeToolCallHook if configured (Req 20.5, 20.6)
        if (config.getBeforeToolCall() != null) {
            // Extract AssistantMessage from AgentMessage via MessageAdapter
            AssistantMessage assistantMessage = null;
            if (assistantMsg instanceof MessageAdapter adapter
                    && adapter.message() instanceof AssistantMessage am) {
                assistantMessage = am;
            }

            if (assistantMessage != null) {
                BeforeToolCallContext beforeContext = new BeforeToolCallContext(
                        assistantMessage, toolCall, toolCall.arguments(), context);
                try {
                    BeforeToolCallResult beforeResult =
                            config.getBeforeToolCall().call(beforeContext, signal).join();
                    if (beforeResult != null && Boolean.TRUE.equals(beforeResult.block())) {
                        String reason = beforeResult.reason() != null
                                ? beforeResult.reason()
                                : "Tool execution was blocked";
                        return new PrepareResult.Immediate(
                                createErrorToolResult(reason), true);
                    }
                } catch (Exception e) {
                    return new PrepareResult.Immediate(
                            createErrorToolResult("BeforeToolCallHook failed: " + e.getMessage()), true);
                }
            }
        }

        // 4. All checks passed (Req 20.7)
        return new PrepareResult.Prepared(toolCall, tool, toolCall.arguments());
    }

    // ── Tool execution ──────────────────────────────────────────────────

    /**
     * Executes a prepared tool call by invoking the tool's {@code execute} method.
     *
     * <p>Creates an {@code onUpdate} callback that emits
     * {@link AgentEvent.ToolExecutionUpdate} events via the stream for each
     * partial result reported by the tool during execution.
     *
     * <p>On success, returns the tool result with {@code isError=false}.
     * On exception, captures the error message and returns an error
     * {@link AgentToolResult} with {@code isError=true}.
     *
     * <p><b>Validates: Requirements 21.1, 21.2, 21.3, 21.4</b>
     *
     * @param prepared the prepared tool call (toolCall, tool, args)
     * @param signal   optional cancellation signal
     * @param stream   the event stream to emit tool_execution_update events
     * @return an {@link ExecuteResult} containing the tool result and error flag
     */
    static ExecuteResult executePreparedToolCall(
            PrepareResult.Prepared prepared,
            CancellationSignal signal,
            EventStream<AgentEvent, List<AgentMessage>> stream) {

        // Create onUpdate callback that emits tool_execution_update events (Req 21.2)
        AgentToolUpdateCallback onUpdate = (partialResult) -> {
            stream.push(new AgentEvent.ToolExecutionUpdate(
                    prepared.toolCall().id(),
                    prepared.tool().name(),
                    prepared.args(),
                    partialResult));
        };

        try {
            // Convert args Object to JsonNode for AgentTool.execute (Req 21.1)
            JsonNode argsAsJsonNode = PiAiJson.MAPPER.valueToTree(prepared.args());

            // Call AgentTool.execute and block for result (Req 21.1)
            AgentToolResult<?> result = prepared.tool()
                    .execute(prepared.toolCall().id(), argsAsJsonNode, signal, onUpdate)
                    .join();

            // Req 21.3: successful execution
            return new ExecuteResult(result, false);
        } catch (Exception e) {
            // Req 21.4: capture exception and return error result
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Tool execution failed";
            // Unwrap CompletionException to get the root cause message
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null) {
                errorMessage = cause.getMessage();
            }
            return new ExecuteResult(createErrorToolResult(errorMessage), true);
        }
    }

    /**
     * Creates an error {@link AgentToolResult} containing a single {@link TextContent}
     * with the given error message and an empty details map.
     *
     * @param message the error message
     * @return an error tool result
     */
    static AgentToolResult<?> createErrorToolResult(String message) {
        return new AgentToolResult<>(
                List.of(new TextContent(message)),
                Map.of()
        );
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
