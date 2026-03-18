package com.pi.coding.session;

/**
 * Marker interface for all agent session events.
 *
 * <p>Includes both standard {@link com.pi.agent.event.AgentEvent} events
 * (forwarded from the underlying Agent) and coding-agent specific events
 * like auto-compaction and auto-retry.
 *
 * <p><b>Validates: Requirement 2.4</b>
 */
public interface AgentSessionEvent {
}
