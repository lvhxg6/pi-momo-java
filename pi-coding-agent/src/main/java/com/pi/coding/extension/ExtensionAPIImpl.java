package com.pi.coding.extension;

import com.pi.ai.core.types.ImageContent;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.ThinkingLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Implementation of ExtensionAPI used during extension loading.
 *
 * <p>Collects registrations and builds an Extension object.
 */
class ExtensionAPIImpl implements ExtensionAPI {

    private final ExtensionRunner runner;
    private final Map<String, List<ExtensionEventHandler<?>>> handlers = new HashMap<>();
    private final Map<String, RegisteredTool> tools = new HashMap<>();
    private final Map<String, RegisteredCommand> commands = new HashMap<>();
    private final Map<String, RegisteredShortcut> shortcuts = new HashMap<>();
    private final Map<String, RegisteredFlag> flags = new HashMap<>();
    private final Map<String, Object> flagValues = new HashMap<>();
    private Runnable disposeHandler;
    private String extensionPath = "<inline>";

    ExtensionAPIImpl(ExtensionRunner runner) {
        this.runner = runner;
    }

    void setExtensionPath(String path) {
        this.extensionPath = path;
    }

    Extension buildExtension() {
        return new Extension(
            extensionPath,
            Map.copyOf(handlers),
            Map.copyOf(tools),
            Map.copyOf(commands),
            Map.copyOf(shortcuts),
            Map.copyOf(flags),
            disposeHandler
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tool Registration
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void registerTool(ToolDefinition tool) {
        tools.put(tool.name(), new RegisteredTool(tool, extensionPath));
    }

    @Override
    public void unregisterTool(String name) {
        tools.remove(name);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Command Registration
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void registerCommand(CommandDefinition command) {
        commands.put(command.name(), new RegisteredCommand(command, extensionPath));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Shortcut Registration
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void registerShortcut(ShortcutDefinition shortcut) {
        shortcuts.put(shortcut.key(), new RegisteredShortcut(shortcut, extensionPath));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Flag Registration
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void registerFlag(FlagDefinition flag) {
        flags.put(flag.name(), new RegisteredFlag(flag, extensionPath));
        if (flag.defaultValue() != null) {
            flagValues.put(flag.name(), flag.defaultValue());
        }
    }

    @Override
    public Object getFlag(String name) {
        if (!flags.containsKey(name)) {
            return null;
        }
        return flagValues.get(name);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Event Subscription
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public <T extends ExtensionEvent> Runnable on(Class<T> eventType, ExtensionEventHandler<T> handler) {
        String typeName = getEventTypeName(eventType);
        return on(typeName, handler);
    }

    @Override
    public Runnable on(String eventType, ExtensionEventHandler<? extends ExtensionEvent> handler) {
        List<ExtensionEventHandler<?>> eventHandlers = handlers.computeIfAbsent(eventType, k -> new ArrayList<>());
        eventHandlers.add(handler);
        return () -> eventHandlers.remove(handler);
    }

    private String getEventTypeName(Class<? extends ExtensionEvent> eventType) {
        // Map class to event type name
        String simpleName = eventType.getSimpleName();
        // Convert CamelCase to snake_case
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(c));
        }
        // Remove "Event" suffix if present
        String name = result.toString();
        if (name.endsWith("_event")) {
            name = name.substring(0, name.length() - 6);
        }
        return name;
    }


    // ══════════════════════════════════════════════════════════════════════════
    // Message Sending (stub implementations - will be connected to runtime)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void sendMessage(String text) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("sendMessage not available during extension loading");
    }

    @Override
    public void sendMessage(String text, List<ImageContent> images) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("sendMessage not available during extension loading");
    }

    @Override
    public <T> void sendCustomMessage(String customType, Object content, boolean display, T details, SendMessageOptions options) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("sendCustomMessage not available during extension loading");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Session Operations (stub implementations)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public <T> void appendCustomEntry(String customType, T data) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("appendCustomEntry not available during extension loading");
    }

    @Override
    public <T> void appendCustomMessage(String customType, Object content, boolean display, T details) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("appendCustomMessage not available during extension loading");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tool Management (stub implementations)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public List<String> getActiveToolNames() {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("getActiveToolNames not available during extension loading");
    }

    @Override
    public void setActiveToolNames(List<String> toolNames) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("setActiveToolNames not available during extension loading");
    }

    @Override
    public List<ToolInfo> getAllTools() {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("getAllTools not available during extension loading");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Model Management (stub implementations)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public Model getModel() {
        // TODO: Connect to runtime
        return null;
    }

    @Override
    public CompletableFuture<Boolean> setModel(Model model) {
        // TODO: Connect to runtime
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("setModel not available during extension loading"));
    }

    @Override
    public ThinkingLevel getThinkingLevel() {
        // TODO: Connect to runtime
        return ThinkingLevel.MINIMAL;
    }

    @Override
    public void setThinkingLevel(ThinkingLevel level) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("setThinkingLevel not available during extension loading");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Provider Registration (queued during loading)
    // ══════════════════════════════════════════════════════════════════════════

    private final List<ProviderRegistration> pendingProviderRegistrations = new ArrayList<>();
    private final List<String> pendingProviderUnregistrations = new ArrayList<>();

    @Override
    public void registerProvider(String name, ProviderConfig config) {
        pendingProviderRegistrations.add(new ProviderRegistration(name, config));
    }

    @Override
    public void unregisterProvider(String name) {
        pendingProviderUnregistrations.add(name);
    }

    List<ProviderRegistration> getPendingProviderRegistrations() {
        return pendingProviderRegistrations;
    }

    List<String> getPendingProviderUnregistrations() {
        return pendingProviderUnregistrations;
    }

    record ProviderRegistration(String name, ProviderConfig config) { }

    // ══════════════════════════════════════════════════════════════════════════
    // Event Bus
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public EventBus getEventBus() {
        return runner.getEventBus();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Session Metadata (stub implementations)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void setSessionName(String name) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("setSessionName not available during extension loading");
    }

    @Override
    public String getSessionName() {
        // TODO: Connect to runtime
        return null;
    }

    @Override
    public void setLabel(String entryId, String label) {
        // TODO: Connect to runtime
        throw new UnsupportedOperationException("setLabel not available during extension loading");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Commands
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public List<CommandInfo> getCommands() {
        // TODO: Connect to runtime
        return List.of();
    }
}
