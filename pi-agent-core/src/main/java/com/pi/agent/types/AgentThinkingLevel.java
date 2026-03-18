package com.pi.agent.types;

import com.pi.ai.core.types.ThinkingLevel;

/**
 * Agent-layer thinking level that extends pi-ai-core's {@link ThinkingLevel}
 * with an {@link #OFF} option to disable reasoning entirely.
 *
 * <p>When {@code OFF}, the agent sets reasoning to {@code null} in the loop config.
 * All other values map directly to the corresponding {@link ThinkingLevel} constant.
 */
public enum AgentThinkingLevel {

    /** Disable reasoning entirely — maps to {@code null} (no reasoning). */
    OFF,

    /** Maps to {@link ThinkingLevel#MINIMAL}. */
    MINIMAL,

    /** Maps to {@link ThinkingLevel#LOW}. */
    LOW,

    /** Maps to {@link ThinkingLevel#MEDIUM}. */
    MEDIUM,

    /** Maps to {@link ThinkingLevel#HIGH}. */
    HIGH,

    /** Maps to {@link ThinkingLevel#XHIGH}. */
    XHIGH;

    /**
     * Converts this agent-layer thinking level to the pi-ai-core {@link ThinkingLevel}.
     *
     * @return the corresponding {@link ThinkingLevel}, or {@code null} if this is {@link #OFF}
     */
    public ThinkingLevel toPiAiThinkingLevel() {
        if (this == OFF) {
            return null;
        }
        return ThinkingLevel.valueOf(this.name());
    }
}
