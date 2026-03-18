package com.pi.coding.session;

/**
 * Event emitted when auto-retry starts.
 *
 * @param attempt  Current retry attempt number (1-based)
 * @param delayMs  Delay before retry in milliseconds
 * @param reason   The error reason triggering retry
 */
public record AutoRetryStartEvent(
        int attempt,
        long delayMs,
        String reason
) implements AgentSessionEvent {
}
