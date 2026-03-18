package com.pi.coding.session;

import com.pi.agent.Agent;
import com.pi.coding.extension.ToolDefinition;
import com.pi.coding.model.CodingModelRegistry;
import com.pi.coding.resource.ResourceLoader;
import com.pi.coding.settings.SettingsManager;

import java.util.List;

/**
 * Configuration for creating an AgentSession.
 *
 * <p><b>Validates: Requirement 2.1</b>
 *
 * @param agent               The underlying Agent from pi-agent-core
 * @param sessionManager      Session persistence manager
 * @param settingsManager     Settings manager
 * @param cwd                 Current working directory
 * @param scopedModels        Models available for cycling
 * @param resourceLoader      Resource loader for skills, prompts, etc.
 * @param customTools         Custom tool definitions from extensions
 * @param modelRegistry       Model registry for API key resolution
 * @param initialActiveToolNames Initial set of active tool names
 */
public record AgentSessionConfig(
        Agent agent,
        SessionManager sessionManager,
        SettingsManager settingsManager,
        String cwd,
        List<ScopedModel> scopedModels,
        ResourceLoader resourceLoader,
        List<ToolDefinition> customTools,
        CodingModelRegistry modelRegistry,
        List<String> initialActiveToolNames
) {
    public AgentSessionConfig {
        if (agent == null) throw new NullPointerException("agent");
        if (sessionManager == null) throw new NullPointerException("sessionManager");
        if (settingsManager == null) throw new NullPointerException("settingsManager");
        if (cwd == null) cwd = System.getProperty("user.dir");
        if (scopedModels == null) scopedModels = List.of();
        if (customTools == null) customTools = List.of();
        if (initialActiveToolNames == null) initialActiveToolNames = List.of();
    }
}
