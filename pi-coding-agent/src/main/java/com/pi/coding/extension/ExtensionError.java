package com.pi.coding.extension;

/**
 * An error that occurred in an extension event handler.
 *
 * @param event   the event type where the error occurred
 * @param message the error message
 * @param cause   the underlying exception (may be null)
 */
public record ExtensionError(
    String event,
    String message,
    Throwable cause
) { }
