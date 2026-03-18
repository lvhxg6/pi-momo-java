package com.pi.coding.extension;

import com.pi.agent.types.AgentMessage;
import com.pi.ai.core.types.ContentBlock;
import com.pi.ai.core.types.ImageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Extension runner - executes extensions and manages their lifecycle.
 *
 * <p>The runner is responsible for:
 * <ul>
 *   <li>Loading extensions from factory functions</li>
 *   <li>Emitting events to all registered handlers</li>
 *   <li>Managing special event handling (context, tool call/result, input)</li>
 *   <li>Error handling - handlers that throw exceptions are logged and skipped</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.15-5.21</b>
 */
public class ExtensionRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionRunner.class);

    private final List<Extension> extensions = new CopyOnWriteArrayList<>();
    private final Map<String, List<ExtensionEventHandler<?>>> handlers = new ConcurrentHashMap<>();
    private final List<Consumer<ExtensionError>> errorListeners = new CopyOnWriteArrayList<>();
    private final EventBusImpl eventBus = new EventBusImpl();

    private volatile boolean disposed = false;

    /**
     * Load extensions from factory functions.
     *
     * <p><b>Validates: Requirement 5.15</b>
     *
     * @param factories the extension factory functions
     * @return the load result containing loaded extensions and any errors
     */
    public LoadExtensionsResult loadExtensions(List<ExtensionFactory> factories) {
        List<Extension> loadedExtensions = new ArrayList<>();
        List<LoadExtensionsResult.LoadError> errors = new ArrayList<>();

        for (ExtensionFactory factory : factories) {
            try {
                ExtensionAPIImpl api = new ExtensionAPIImpl(this);
                factory.create(api);
                Extension extension = api.buildExtension();
                loadedExtensions.add(extension);
                extensions.add(extension);

                // Register handlers from the extension
                for (Map.Entry<String, List<ExtensionEventHandler<?>>> entry : extension.handlers().entrySet()) {
                    handlers.computeIfAbsent(entry.getKey(), k -> new CopyOnWriteArrayList<>())
                            .addAll(entry.getValue());
                }
            } catch (Exception e) {
                logger.error("Failed to load extension from factory", e);
                errors.add(new LoadExtensionsResult.LoadError(
                    factory.getClass().getName(),
                    e.getMessage()
                ));
            }
        }

        return new LoadExtensionsResult(loadedExtensions, errors);
    }

    /**
     * Emit an event to all registered handlers.
     *
     * <p>Handlers are called in registration order. If a handler throws an exception,
     * the error is logged and the next handler is called.
     *
     * <p><b>Validates: Requirement 5.16</b>
     *
     * @param event the event to emit
     * @param <T>   the event type
     * @return a future that completes when all handlers have been called
     */
    public <T extends ExtensionEvent> CompletableFuture<Void> emit(T event) {
        if (disposed) {
            return CompletableFuture.completedFuture(null);
        }

        String eventType = event.type();
        List<ExtensionEventHandler<?>> eventHandlers = handlers.get(eventType);

        if (eventHandlers == null || eventHandlers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            ExtensionContext context = createContext();
            for (ExtensionEventHandler<?> handler : eventHandlers) {
                try {
                    @SuppressWarnings("unchecked")
                    ExtensionEventHandler<T> typedHandler = (ExtensionEventHandler<T>) handler;
                    CompletableFuture<Object> result = typedHandler.handle(event, context);
                    if (result != null) {
                        result.join(); // Wait for handler to complete
                    }
                } catch (Exception e) {
                    emitError(new ExtensionError(eventType, e.getMessage(), e));
                }
            }
        });
    }

    /**
     * Check if there are handlers registered for an event type.
     *
     * @param eventType the event type name
     * @return true if there are handlers
     */
    public boolean hasHandlers(String eventType) {
        List<ExtensionEventHandler<?>> eventHandlers = handlers.get(eventType);
        return eventHandlers != null && !eventHandlers.isEmpty();
    }


    // ══════════════════════════════════════════════════════════════════════════
    // Special Event Handling (Requirements 5.17-5.20)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Emit context event and collect modified messages.
     *
     * <p>Handlers can modify the messages list. Each handler receives the messages
     * from the previous handler (or the original messages for the first handler).
     *
     * <p><b>Validates: Requirement 5.19</b>
     *
     * @param messages the original messages
     * @return the potentially modified messages
     */
    public CompletableFuture<List<AgentMessage>> emitContext(List<AgentMessage> messages) {
        if (disposed || !hasHandlers("context")) {
            return CompletableFuture.completedFuture(messages);
        }

        return CompletableFuture.supplyAsync(() -> {
            List<AgentMessage> currentMessages = new ArrayList<>(messages);
            ExtensionContext context = createContext();

            for (ExtensionEventHandler<?> handler : handlers.getOrDefault("context", List.of())) {
                try {
                    ExtensionEvent.ContextEvent event = new ExtensionEvent.ContextEvent(currentMessages);
                    @SuppressWarnings("unchecked")
                    ExtensionEventHandler<ExtensionEvent.ContextEvent> typedHandler =
                        (ExtensionEventHandler<ExtensionEvent.ContextEvent>) handler;
                    CompletableFuture<Object> result = typedHandler.handle(event, context);
                    if (result != null) {
                        Object handlerResult = result.join();
                        if (handlerResult instanceof EventResult.ContextEventResult contextResult) {
                            if (contextResult.messages() != null) {
                                currentMessages = contextResult.messages();
                            }
                        }
                    }
                } catch (Exception e) {
                    emitError(new ExtensionError("context", e.getMessage(), e));
                }
            }

            return currentMessages;
        });
    }

    /**
     * Emit before_provider_request event and collect modified payload.
     *
     * <p>Handlers can replace the payload. Each handler receives the payload
     * from the previous handler (or the original payload for the first handler).
     *
     * <p><b>Validates: Requirement 5.20</b>
     *
     * @param payload the original payload
     * @return the potentially modified payload
     */
    public CompletableFuture<Object> emitBeforeProviderRequest(Object payload) {
        if (disposed || !hasHandlers("before_provider_request")) {
            return CompletableFuture.completedFuture(payload);
        }

        return CompletableFuture.supplyAsync(() -> {
            Object currentPayload = payload;
            ExtensionContext context = createContext();

            for (ExtensionEventHandler<?> handler : handlers.getOrDefault("before_provider_request", List.of())) {
                try {
                    ExtensionEvent.BeforeProviderRequestEvent event =
                        new ExtensionEvent.BeforeProviderRequestEvent(currentPayload);
                    @SuppressWarnings("unchecked")
                    ExtensionEventHandler<ExtensionEvent.BeforeProviderRequestEvent> typedHandler =
                        (ExtensionEventHandler<ExtensionEvent.BeforeProviderRequestEvent>) handler;
                    CompletableFuture<Object> result = typedHandler.handle(event, context);
                    if (result != null) {
                        Object handlerResult = result.join();
                        if (handlerResult != null) {
                            currentPayload = handlerResult;
                        }
                    }
                } catch (Exception e) {
                    emitError(new ExtensionError("before_provider_request", e.getMessage(), e));
                }
            }

            return currentPayload;
        });
    }

    /**
     * Emit input event and collect result.
     *
     * <p>Handlers can transform the input or indicate it was fully handled.
     * If any handler returns "handled", processing stops.
     *
     * @param text   the input text
     * @param images attached images (may be null)
     * @param source input source
     * @return the input event result
     */
    public CompletableFuture<EventResult.InputEventResult> emitInput(
            String text, List<ImageContent> images, String source) {
        if (disposed || !hasHandlers("input")) {
            return CompletableFuture.completedFuture(new EventResult.InputEventResult.Continue());
        }

        return CompletableFuture.supplyAsync(() -> {
            String currentText = text;
            List<ImageContent> currentImages = images;
            ExtensionContext context = createContext();

            for (ExtensionEventHandler<?> handler : handlers.getOrDefault("input", List.of())) {
                try {
                    ExtensionEvent.InputEvent event = new ExtensionEvent.InputEvent(currentText, currentImages, source);
                    @SuppressWarnings("unchecked")
                    ExtensionEventHandler<ExtensionEvent.InputEvent> typedHandler =
                        (ExtensionEventHandler<ExtensionEvent.InputEvent>) handler;
                    CompletableFuture<Object> result = typedHandler.handle(event, context);
                    if (result != null) {
                        Object handlerResult = result.join();
                        if (handlerResult instanceof EventResult.InputEventResult inputResult) {
                            if (inputResult instanceof EventResult.InputEventResult.Handled) {
                                return inputResult;
                            } else if (inputResult instanceof EventResult.InputEventResult.Transform transform) {
                                currentText = transform.text();
                                if (transform.images() != null) {
                                    currentImages = transform.images();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    emitError(new ExtensionError("input", e.getMessage(), e));
                }
            }

            if (!currentText.equals(text) || currentImages != images) {
                return new EventResult.InputEventResult.Transform(currentText, currentImages);
            }
            return new EventResult.InputEventResult.Continue();
        });
    }


    /**
     * Emit tool_call event and check if the tool should be blocked.
     *
     * <p><b>Validates: Requirement 5.17</b>
     *
     * @param toolCallId the tool call ID
     * @param toolName   the tool name
     * @param input      the tool input
     * @return the tool call event result (may indicate blocking)
     */
    public CompletableFuture<EventResult.ToolCallEventResult> emitToolCall(
            String toolCallId, String toolName, Object input) {
        if (disposed || !hasHandlers("tool_call")) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            ExtensionContext context = createContext();
            EventResult.ToolCallEventResult result = null;

            for (ExtensionEventHandler<?> handler : handlers.getOrDefault("tool_call", List.of())) {
                try {
                    ExtensionEvent.ToolCallEvent event = new ExtensionEvent.ToolCallEvent(toolCallId, toolName, input);
                    @SuppressWarnings("unchecked")
                    ExtensionEventHandler<ExtensionEvent.ToolCallEvent> typedHandler =
                        (ExtensionEventHandler<ExtensionEvent.ToolCallEvent>) handler;
                    CompletableFuture<Object> handlerFuture = typedHandler.handle(event, context);
                    if (handlerFuture != null) {
                        Object handlerResult = handlerFuture.join();
                        if (handlerResult instanceof EventResult.ToolCallEventResult toolCallResult) {
                            result = toolCallResult;
                            if (Boolean.TRUE.equals(toolCallResult.block())) {
                                return result; // Short-circuit on block
                            }
                        }
                    }
                } catch (Exception e) {
                    emitError(new ExtensionError("tool_call", e.getMessage(), e));
                }
            }

            return result;
        });
    }

    /**
     * Emit tool_result event and collect modified result.
     *
     * <p><b>Validates: Requirement 5.18</b>
     *
     * @param toolCallId the tool call ID
     * @param toolName   the tool name
     * @param input      the tool input
     * @param content    the result content
     * @param details    the result details
     * @param isError    whether the result is an error
     * @return the tool result event result (may contain modifications)
     */
    public CompletableFuture<EventResult.ToolResultEventResult> emitToolResult(
            String toolCallId, String toolName, Object input,
            List<ContentBlock> content, Object details, boolean isError) {
        if (disposed || !hasHandlers("tool_result")) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            List<ContentBlock> currentContent = content;
            Object currentDetails = details;
            boolean currentIsError = isError;
            boolean modified = false;
            ExtensionContext context = createContext();

            for (ExtensionEventHandler<?> handler : handlers.getOrDefault("tool_result", List.of())) {
                try {
                    ExtensionEvent.ToolResultEvent event = new ExtensionEvent.ToolResultEvent(
                        toolCallId, toolName, input, currentContent, currentDetails, currentIsError
                    );
                    @SuppressWarnings("unchecked")
                    ExtensionEventHandler<ExtensionEvent.ToolResultEvent> typedHandler =
                        (ExtensionEventHandler<ExtensionEvent.ToolResultEvent>) handler;
                    CompletableFuture<Object> handlerFuture = typedHandler.handle(event, context);
                    if (handlerFuture != null) {
                        Object handlerResult = handlerFuture.join();
                        if (handlerResult instanceof EventResult.ToolResultEventResult toolResultResult) {
                            if (toolResultResult.content() != null) {
                                currentContent = toolResultResult.content();
                                modified = true;
                            }
                            if (toolResultResult.details() != null) {
                                currentDetails = toolResultResult.details();
                                modified = true;
                            }
                            if (toolResultResult.isError() != null) {
                                currentIsError = toolResultResult.isError();
                                modified = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    emitError(new ExtensionError("tool_result", e.getMessage(), e));
                }
            }

            if (!modified) {
                return null;
            }

            return new EventResult.ToolResultEventResult(currentContent, currentDetails, currentIsError);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error Handling (Requirement 5.21)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register an error listener.
     *
     * @param listener the error listener
     * @return a runnable to unregister the listener
     */
    public Runnable onError(Consumer<ExtensionError> listener) {
        errorListeners.add(listener);
        return () -> errorListeners.remove(listener);
    }

    /**
     * Emit an error to all registered listeners.
     *
     * @param error the error
     */
    private void emitError(ExtensionError error) {
        logger.warn("Extension error in event '{}': {}", error.event(), error.message(), error.cause());
        for (Consumer<ExtensionError> listener : errorListeners) {
            try {
                listener.accept(error);
            } catch (Exception e) {
                logger.error("Error in error listener", e);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Dispose the runner and all loaded extensions.
     *
     * <p>After disposal, no more events will be emitted.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        for (Extension extension : extensions) {
            try {
                if (extension.disposeHandler() != null) {
                    extension.disposeHandler().run();
                }
            } catch (Exception e) {
                logger.error("Error disposing extension", e);
            }
        }

        extensions.clear();
        handlers.clear();
        errorListeners.clear();
    }

    /**
     * Get the event bus for extension-to-extension communication.
     *
     * @return the event bus
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Get all loaded extensions.
     *
     * @return the loaded extensions
     */
    public List<Extension> getExtensions() {
        return List.copyOf(extensions);
    }

    /**
     * Get all registered tools from all extensions.
     *
     * @return the registered tools
     */
    public List<RegisteredTool> getAllRegisteredTools() {
        List<RegisteredTool> tools = new ArrayList<>();
        for (Extension extension : extensions) {
            tools.addAll(extension.tools().values());
        }
        return tools;
    }

    /**
     * Get all registered commands from all extensions.
     *
     * @return the registered commands
     */
    public List<RegisteredCommand> getAllRegisteredCommands() {
        List<RegisteredCommand> commands = new ArrayList<>();
        for (Extension extension : extensions) {
            commands.addAll(extension.commands().values());
        }
        return commands;
    }

    /**
     * Get all registered shortcuts from all extensions.
     *
     * @return the registered shortcuts
     */
    public List<RegisteredShortcut> getAllRegisteredShortcuts() {
        List<RegisteredShortcut> shortcuts = new ArrayList<>();
        for (Extension extension : extensions) {
            shortcuts.addAll(extension.shortcuts().values());
        }
        return shortcuts;
    }

    /**
     * Get all registered flags from all extensions.
     *
     * @return the registered flags
     */
    public List<RegisteredFlag> getAllRegisteredFlags() {
        List<RegisteredFlag> flags = new ArrayList<>();
        for (Extension extension : extensions) {
            flags.addAll(extension.flags().values());
        }
        return flags;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Internal
    // ══════════════════════════════════════════════════════════════════════════

    private ExtensionContext createContext() {
        // TODO: Implement proper context creation with actual session state
        return new ExtensionContextImpl();
    }

    /**
     * Placeholder context implementation.
     */
    private static class ExtensionContextImpl implements ExtensionContext {
        @Override
        public String getCwd() { return System.getProperty("user.dir"); }

        @Override
        public com.pi.coding.session.SessionManager getSessionManager() { return null; }

        @Override
        public com.pi.ai.core.types.Model getModel() { return null; }

        @Override
        public boolean isIdle() { return true; }

        @Override
        public void abort() { }

        @Override
        public boolean hasPendingMessages() { return false; }

        @Override
        public void shutdown() { }

        @Override
        public ContextUsage getContextUsage() { return null; }

        @Override
        public void compact() { }

        @Override
        public void compact(CompactOptions options) { }

        @Override
        public String getSystemPrompt() { return ""; }

        @Override
        public boolean hasUI() { return false; }
    }
}
