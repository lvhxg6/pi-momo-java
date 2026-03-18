package com.pi.coding.extension;

/**
 * Extended EventBus interface with control methods.
 *
 * <p>Provides additional methods for managing the event bus lifecycle,
 * such as clearing all subscribers.
 *
 * <p><b>Validates: Requirements 22.1-22.5</b>
 */
public interface EventBusController extends EventBus {

    /**
     * Clear all subscribers from the event bus.
     */
    void clear();

    /**
     * Create a new EventBusController instance.
     *
     * @return a new EventBusController
     */
    static EventBusController create() {
        return new EventBusImpl();
    }
}
