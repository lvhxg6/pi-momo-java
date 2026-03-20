package com.pi.coding.resource;

import com.pi.coding.extension.LoadExtensionsResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for loading all agent resources: extensions, skills, prompts,
 * context files, and system prompts.
 * 
 * <p>Supports hot-reload functionality through change listeners and file watching.
 */
public interface ResourceLoader {
    
    /**
     * Reload all resources from configured locations.
     */
    CompletableFuture<Void> reload();
    
    /**
     * Get loaded extensions.
     */
    LoadExtensionsResult getExtensions();
    
    /**
     * Get loaded skills and diagnostics.
     */
    LoadSkillsResult getSkills();
    
    /**
     * Get loaded prompt templates.
     */
    LoadPromptsResult getPrompts();
    
    /**
     * Get AGENTS.md context files.
     */
    List<ContextFile> getAgentsFiles();
    
    /**
     * Get custom system prompt, or null for default.
     */
    String getSystemPrompt();
    
    /**
     * Get append system prompt lines.
     */
    List<String> getAppendSystemPrompt();
    
    /**
     * Get resource loading diagnostics.
     */
    List<ResourceDiagnostic> getDiagnostics();
    
    /**
     * Extend resources with additional paths from extensions.
     */
    void extendResources(ResourceExtensionPaths paths);
    
    // ==================== Hot-Reload Support ====================
    
    /**
     * Add a listener to be notified when resources change.
     *
     * @param listener the listener to add
     */
    default void addChangeListener(ResourceChangeListener listener) {
        // Default no-op implementation for backward compatibility
    }
    
    /**
     * Remove a previously added change listener.
     *
     * @param listener the listener to remove
     */
    default void removeChangeListener(ResourceChangeListener listener) {
        // Default no-op implementation for backward compatibility
    }
    
    /**
     * Start watching for file changes in resource directories.
     * 
     * <p>When file changes are detected, resources will be automatically
     * reloaded and registered listeners will be notified.
     */
    default void startWatching() {
        // Default no-op implementation for backward compatibility
    }
    
    /**
     * Stop watching for file changes.
     */
    default void stopWatching() {
        // Default no-op implementation for backward compatibility
    }
    
    /**
     * Dispose of all resources and stop watching.
     * 
     * <p>This method should be called when the ResourceLoader is no longer needed
     * to ensure proper cleanup of file watchers and other resources.
     */
    default void dispose() {
        stopWatching();
    }
}
