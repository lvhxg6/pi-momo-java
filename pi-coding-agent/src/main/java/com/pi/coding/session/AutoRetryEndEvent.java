package com.pi.coding.session;

/**
 * Event emitted when auto-retry ends.
 *
 * @param attempt  The retry attempt number that completed
 * @param success  Whether the retry was successful
 * @param aborted  Whether the retry was aborted by user
 */
public record AutoRetryEndEvent(
        int attempt,
        boolean success,
        boolean aborted
) implements AgentSessionEvent {
}
