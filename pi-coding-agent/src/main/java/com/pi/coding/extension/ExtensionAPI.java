package com.pi.coding.extension;

import com.pi.agent.types.AgentTool;
import com.pi.ai.core.types.ImageContent;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.ThinkingLevel;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * API provided to extension factory functions.
 *
 * <p>Extensions use this API to:
 * <ul>
 *   <li>Register tools, commands, shortcuts, and CLI flags</li>
 *   <li>Subscribe to agent lifecycle events</li>
 *   <li>Send messages and append entries to the session</li>
 *   <li>Manage active tools and model settings</li>
 *   <li>Register custom providers</li>
 *   <li>Access the event bus for extension-to-extension communication</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.3-5.14</b>
 */
public interface ExtensionAPI {

    // ══════════════════════════════════════════════════════════════════════════
    // Tool Registration (Requirement 5.3)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register a tool that the LLM can call.
     *
     * @param tool the tool definition
     */
    void registerTool(ToolDefinition tool);

    /**
     * Unregister a previously registered tool.
     *
     * @param name the tool name to unregister
     */
    void unregisterTool(String name);

    // ══════════════════════════════════════════════════════════════════════════
    // Command Registration (Requirement 5.4)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register a custom slash command.
     *
     * @param command the command definition
     */
    void registerCommand(CommandDefinition command);

    // ══════════════════════════════════════════════════════════════════════════
    // Shortcut Registration (Requirement 5.5)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register a keyboard shortcut.
     *
     * @param shortcut the shortcut definition
     */
    void registerShortcut(ShortcutDefinition shortcut);

    // ══════════════════════════════════════════════════════════════════════════
    // CLI Flag Registration (Requirement 5.6)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register a CLI flag.
     *
     * @param flag the flag definition
     */
    void registerFlag(FlagDefinition flag);

    /**
     * Get the value of a registered CLI flag.
     *
     * @param name the flag name
     * @return the flag value, or null if not registered
     */
    Object getFlag(String name);

    // ══════════════════════════════════════════════════════════════════════════
    // Event Subscription (Requirement 5.8)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Subscribe to an extension event.
     *
     * @param eventType the event class to subscribe to
     * @param handler   the event handler
     * @param <T>       the event type
     * @return a runnable to unsubscribe
     */
    <T extends ExtensionEvent> Runnable on(Class<T> eventType, ExtensionEventHandler<T> handler);

    /**
     * Subscribe to an extension event by type name.
     *
     * @param eventType the event type name (e.g., "session_start")
     * @param handler   the event handler
     * @return a runnable to unsubscribe
     */
    Runnable on(String eventType, ExtensionEventHandler<? extends ExtensionEvent> handler);

    // ══════════════════════════════════════════════════════════════════════════
    // Message Sending (Requirement 5.9)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Send a text message to the agent.
     *
     * @param text the message text
     */
    void sendMessage(String text);

    /**
     * Send a message with images to the agent.
     *
     * @param text   the message text
     * @param images attached images
     */
    void sendMessage(String text, List<ImageContent> images);

    /**
     * Send a custom message to the session.
     *
     * @param customType custom type identifier
     * @param content    message content
     * @param display    whether to display in UI
     * @param details    additional details (may be null)
     * @param options    send options (may be null)
     * @param <T>        the details type
     */
    <T> void sendCustomMessage(String customType, Object content, boolean display, T details, SendMessageOptions options);

    // ══════════════════════════════════════════════════════════════════════════
    // Session Operations (Requirement 5.10)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Append a custom entry to the session for state persistence (not sent to LLM).
     *
     * @param customType custom type identifier
     * @param data       entry data (may be null)
     * @param <T>        the data type
     */
    <T> void appendCustomEntry(String customType, T data);

    /**
     * Append a custom message entry to the session that participates in LLM context.
     *
     * @param customType custom type identifier
     * @param content    message content
     * @param display    whether to display in UI
     * @param details    additional details (may be null)
     * @param <T>        the details type
     */
    <T> void appendCustomMessage(String customType, Object content, boolean display, T details);

    // ══════════════════════════════════════════════════════════════════════════
    // Tool Management (Requirement 5.11)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Get the list of currently active tool names.
     *
     * @return list of active tool names
     */
    List<String> getActiveToolNames();

    /**
     * Set the active tools by name.
     *
     * @param toolNames list of tool names to activate
     */
    void setActiveToolNames(List<String> toolNames);

    /**
     * Get all configured tools with name and description.
     *
     * @return list of tool info
     */
    List<ToolInfo> getAllTools();

    // ══════════════════════════════════════════════════════════════════════════
    // Model Management (Requirement 5.12)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Get the current model.
     *
     * @return the current model, or null if not set
     */
    Model getModel();

    /**
     * Set the current model.
     *
     * @param model the model to set
     * @return a future that completes with true if successful, false if no API key available
     */
    CompletableFuture<Boolean> setModel(Model model);

    /**
     * Get the current thinking level.
     *
     * @return the current thinking level
     */
    ThinkingLevel getThinkingLevel();

    /**
     * Set the thinking level (clamped to model capabilities).
     *
     * @param level the thinking level to set
     */
    void setThinkingLevel(ThinkingLevel level);

    // ══════════════════════════════════════════════════════════════════════════
    // Provider Registration (Requirement 5.13)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register or override a model provider.
     *
     * <p>If {@code models} is provided: replaces all existing models for this provider.
     * If only {@code baseUrl} is provided: overrides the URL for existing models.
     * If {@code oauth} is provided: registers OAuth provider for /login support.
     *
     * <p>During initial extension load this call is queued and applied once the
     * runner has bound its context. After that it takes effect immediately.
     *
     * @param name   provider name
     * @param config provider configuration
     */
    void registerProvider(String name, ProviderConfig config);

    /**
     * Unregister a previously registered provider.
     *
     * <p>Removes all models belonging to the named provider and restores any
     * built-in models that were overridden by it.
     *
     * @param name provider name to unregister
     */
    void unregisterProvider(String name);

    // ══════════════════════════════════════════════════════════════════════════
    // Event Bus (Requirement 5.14)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Get the shared event bus for extension-to-extension communication.
     *
     * @return the event bus
     */
    EventBus getEventBus();

    // ══════════════════════════════════════════════════════════════════════════
    // Session Metadata
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Set the session display name (shown in session selector).
     *
     * @param name the session name
     */
    void setSessionName(String name);

    /**
     * Get the current session name, if set.
     *
     * @return the session name, or null if not set
     */
    String getSessionName();

    /**
     * Set or clear a label on an entry.
     *
     * @param entryId the entry ID
     * @param label   the label to set, or null to clear
     */
    void setLabel(String entryId, String label);

    // ══════════════════════════════════════════════════════════════════════════
    // Commands
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Get available slash commands in the current session.
     *
     * @return list of command info
     */
    List<CommandInfo> getCommands();
}
