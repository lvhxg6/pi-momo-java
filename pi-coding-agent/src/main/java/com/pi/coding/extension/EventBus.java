package com.pi.coding.extension;

import java.util.function.Consumer;

/**
 * Event bus for extension-to-extension communication.
 *
 * <p>Allows extensions to subscribe to named events and emit events to all subscribers.
 *
 * <p><b>Validates: Requirements 22.1-22.5</b>
 */
public interface EventBus {

    /**
     * Subscribe to a named event.
     *
     * @param eventName the event name
     * @param handler   the event handler
     * @param <T>       the event data type
     * @return a runnable to unsubscribe
     */
    <T> Runnable on(String eventName, Consumer<T> handler);

    /**
     * Emit an event to all subscribers.
     *
     * @param eventName the event name
     * @param data      the event data
     * @param <T>       the event data type
     */
    <T> void emit(String eventName, T data);
}
