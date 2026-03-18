package com.pi.coding.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Default implementation of EventBus for extension-to-extension communication.
 *
 * <p>Uses ConcurrentHashMap for thread-safe storage of subscribers.
 * Handler exceptions are caught and logged to prevent one handler from
 * affecting others.
 *
 * <p><b>Validates: Requirements 22.1-22.5</b>
 */
public class EventBusImpl implements EventBusController {

    private static final Logger logger = LoggerFactory.getLogger(EventBusImpl.class);

    private final Map<String, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public <T> Runnable on(String eventName, Consumer<T> handler) {
        List<Consumer<?>> handlers = subscribers.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>());
        handlers.add(handler);
        return () -> handlers.remove(handler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void emit(String eventName, T data) {
        List<Consumer<?>> handlers = subscribers.get(eventName);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        for (Consumer<?> handler : handlers) {
            try {
                ((Consumer<T>) handler).accept(data);
            } catch (Exception e) {
                // Log error but continue to other handlers
                // Validates: Requirement 22.4 - handler isolation
                logger.warn("Error in event bus handler for event '{}': {}", eventName, e.getMessage(), e);
            }
        }
    }

    /**
     * Clear all subscribers.
     */
    public void clear() {
        subscribers.clear();
    }
}
