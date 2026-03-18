package com.pi.coding.extension;

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface for extension event handlers.
 *
 * <p>Event handlers receive an event and an extension context, and may return
 * a result that modifies agent behavior.
 *
 * @param <T> the event type
 */
@FunctionalInterface
public interface ExtensionEventHandler<T extends ExtensionEvent> {

    /**
     * Handle an extension event.
     *
     * @param event   the event
     * @param context the extension context
     * @return a future that completes with the handler result (may be null)
     */
    CompletableFuture<Object> handle(T event, ExtensionContext context);
}
