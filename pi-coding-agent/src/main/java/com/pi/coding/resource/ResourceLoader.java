package com.pi.coding.resource;

import com.pi.coding.extension.LoadExtensionsResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for loading all agent resources: extensions, skills, prompts,
 * context files, and system prompts.
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
}
