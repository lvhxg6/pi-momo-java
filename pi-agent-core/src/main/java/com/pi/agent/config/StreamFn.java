package com.pi.agent.config;

import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.types.Context;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.SimpleStreamOptions;

/**
 * Replaceable streaming function type for LLM calls.
 *
 * <p>The signature matches {@code PiAi.streamSimple}: accepts a {@link Model},
 * {@link Context}, and {@link SimpleStreamOptions}, returning an
 * {@link AssistantMessageEventStream}. When not configured, the agent loop
 * defaults to {@code PiAi.streamSimple}.
 *
 * <p>Implementations must not throw exceptions; failures should be expressed
 * through the returned event stream's error event and a {@code stopReason}
 * of {@code error} or {@code aborted}.
 *
 * <p><b>Validates: Requirements 12.1</b>
 *
 * @see com.pi.ai.core.PiAi#streamSimple
 */
@FunctionalInterface
public interface StreamFn {

    /**
     * Invokes the LLM streaming call.
     *
     * @param model   the target LLM model
     * @param context the conversation context (system prompt, messages, tools)
     * @param options stream options (temperature, maxTokens, reasoning, etc.)
     * @return an event stream of assistant message events
     */
    AssistantMessageEventStream stream(Model model, Context context, SimpleStreamOptions options);
}
