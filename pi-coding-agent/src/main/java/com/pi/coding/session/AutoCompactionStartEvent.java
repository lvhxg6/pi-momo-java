package com.pi.coding.session;

/**
 * Event emitted when auto-compaction starts.
 *
 * @param reason The reason for compaction ("overflow" or "threshold")
 */
public record AutoCompactionStartEvent(String reason) implements AgentSessionEvent {
}
