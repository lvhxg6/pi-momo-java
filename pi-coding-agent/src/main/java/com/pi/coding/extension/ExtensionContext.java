package com.pi.coding.extension;

import com.pi.ai.core.types.Model;
import com.pi.coding.session.SessionManager;

/**
 * Context passed to extension event handlers.
 *
 * <p>Provides access to session state, model information, and agent control methods.
 */
public interface ExtensionContext {

    /**
     * Get the current working directory.
     *
     * @return the current working directory
     */
    String getCwd();

    /**
     * Get the session manager (read-only).
     *
     * @return the session manager
     */
    SessionManager getSessionManager();

    /**
     * Get the current model.
     *
     * @return the current model, or null if not set
     */
    Model getModel();

    /**
     * Check if the agent is idle (not streaming).
     *
     * @return true if idle
     */
    boolean isIdle();

    /**
     * Abort the current agent operation.
     */
    void abort();

    /**
     * Check if there are queued messages waiting.
     *
     * @return true if there are pending messages
     */
    boolean hasPendingMessages();

    /**
     * Gracefully shutdown and exit.
     */
    void shutdown();

    /**
     * Get current context usage for the active model.
     *
     * @return context usage, or null if not available
     */
    ContextUsage getContextUsage();

    /**
     * Trigger compaction without awaiting completion.
     */
    void compact();

    /**
     * Trigger compaction with options.
     *
     * @param options compaction options
     */
    void compact(CompactOptions options);

    /**
     * Get the current effective system prompt.
     *
     * @return the system prompt
     */
    String getSystemPrompt();

    /**
     * Check if UI is available.
     *
     * @return true if UI is available (false in print/RPC mode)
     */
    boolean hasUI();
}
