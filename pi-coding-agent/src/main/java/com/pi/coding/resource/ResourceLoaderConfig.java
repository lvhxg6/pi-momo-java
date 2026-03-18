package com.pi.coding.resource;

import com.pi.coding.settings.SettingsManager;

/**
 * Configuration for the DefaultResourceLoader.
 */
public record ResourceLoaderConfig(
    String cwd,
    String agentDir,
    SettingsManager settingsManager
) {
    public ResourceLoaderConfig {
        if (cwd == null || cwd.isEmpty()) {
            throw new IllegalArgumentException("cwd cannot be null or empty");
        }
        if (agentDir == null || agentDir.isEmpty()) {
            throw new IllegalArgumentException("agentDir cannot be null or empty");
        }
        if (settingsManager == null) {
            throw new IllegalArgumentException("settingsManager cannot be null");
        }
    }
}
