package com.pi.coding.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pi.ai.core.util.PiAiJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Settings manager for pi-coding-agent.
 * 
 * <p>Loads settings from two sources and deep-merges them:
 * <ol>
 *   <li>Global settings: {@code ~/.pi/settings.json}</li>
 *   <li>Project settings: {@code {cwd}/.pi/settings.json}</li>
 * </ol>
 * 
 * <p>Project settings take precedence over global settings in case of conflict.
 * Uses file locking to prevent concurrent writes.
 */
public class SettingsManager {

    private static final Logger log = LoggerFactory.getLogger(SettingsManager.class);
    private static final ObjectMapper MAPPER = PiAiJson.MAPPER;

    private final Path globalSettingsPath;
    private final Path projectSettingsPath;

    /** Merged effective settings (project overrides global) */
    private volatile SettingsData effectiveSettings;

    private SettingsManager(Path globalSettingsPath, Path projectSettingsPath) {
        this.globalSettingsPath = globalSettingsPath;
        this.projectSettingsPath = projectSettingsPath;
        this.effectiveSettings = loadAndMerge();
    }

    /**
     * Creates a SettingsManager for the given working directory and agent directory.
     *
     * @param cwd      current working directory
     * @param agentDir agent config directory name (e.g., ".pi")
     * @return a new SettingsManager
     */
    public static SettingsManager create(String cwd, String agentDir) {
        Objects.requireNonNull(cwd, "cwd must not be null");
        Objects.requireNonNull(agentDir, "agentDir must not be null");
        Path home = Path.of(System.getProperty("user.home"));
        Path globalPath = home.resolve(agentDir).resolve("settings.json");
        Path projectPath = Path.of(cwd).resolve(agentDir).resolve("settings.json");
        return new SettingsManager(globalPath, projectPath);
    }

    /**
     * Creates an in-memory SettingsManager with default settings (for testing).
     *
     * @return a new in-memory SettingsManager
     */
    public static SettingsManager inMemory() {
        return new SettingsManager(null, null) {
            private SettingsData data = SettingsData.EMPTY;

            @Override
            public void save(SettingsUpdate update) {
                data = applyUpdate(data, update);
            }

            @Override
            protected SettingsData loadAndMerge() {
                return SettingsData.EMPTY;
            }
        };
    }

    // ========== Basic Settings ==========

    public String getDefaultProvider() {
        return effectiveSettings.defaultProvider() != null
            ? effectiveSettings.defaultProvider() : "anthropic";
    }

    public String getDefaultModel() {
        return effectiveSettings.defaultModel() != null
            ? effectiveSettings.defaultModel() : "claude-sonnet-4-20250514";
    }

    public String getDefaultThinkingLevel() {
        return effectiveSettings.defaultThinkingLevel() != null
            ? effectiveSettings.defaultThinkingLevel() : "none";
    }

    public String getTransport() {
        return effectiveSettings.transport() != null
            ? effectiveSettings.transport() : "http";
    }

    public String getSteeringMode() {
        return effectiveSettings.steeringMode() != null
            ? effectiveSettings.steeringMode() : "auto";
    }

    public String getFollowUpMode() {
        return effectiveSettings.followUpMode() != null
            ? effectiveSettings.followUpMode() : "auto";
    }

    public String getTheme() {
        return effectiveSettings.theme() != null
            ? effectiveSettings.theme() : "default";
    }

    // ========== Feature Settings ==========

    public CompactionSettings getCompactionSettings() {
        return effectiveSettings.compaction() != null
            ? effectiveSettings.compaction() : CompactionSettings.DEFAULT;
    }

    public BranchSummarySettings getBranchSummarySettings() {
        return effectiveSettings.branchSummary() != null
            ? effectiveSettings.branchSummary() : BranchSummarySettings.DEFAULT;
    }

    public RetrySettings getRetrySettings() {
        return effectiveSettings.retry() != null
            ? effectiveSettings.retry() : RetrySettings.DEFAULT;
    }

    // ========== Terminal Settings ==========

    public boolean getShowImages() {
        return effectiveSettings.showImages() != null
            ? effectiveSettings.showImages() : true;
    }

    public boolean getClearOnShrink() {
        return effectiveSettings.clearOnShrink() != null
            ? effectiveSettings.clearOnShrink() : false;
    }

    // ========== Image Settings ==========

    public boolean getAutoResize() {
        return effectiveSettings.autoResize() != null
            ? effectiveSettings.autoResize() : true;
    }

    public boolean getBlockImages() {
        return effectiveSettings.blockImages() != null
            ? effectiveSettings.blockImages() : false;
    }

    // ========== Thinking Budgets ==========

    public ThinkingBudgets getThinkingBudgets() {
        return effectiveSettings.thinkingBudgets() != null
            ? effectiveSettings.thinkingBudgets() : ThinkingBudgets.DEFAULT;
    }

    // ========== Path Settings ==========

    public List<String> getExtensionPaths() {
        return effectiveSettings.extensionPaths() != null
            ? effectiveSettings.extensionPaths() : Collections.emptyList();
    }

    public List<String> getSkillPaths() {
        return effectiveSettings.skillPaths() != null
            ? effectiveSettings.skillPaths() : Collections.emptyList();
    }

    public List<String> getPromptPaths() {
        return effectiveSettings.promptPaths() != null
            ? effectiveSettings.promptPaths() : Collections.emptyList();
    }

    public List<String> getThemePaths() {
        return effectiveSettings.themePaths() != null
            ? effectiveSettings.themePaths() : Collections.emptyList();
    }

    // ========== Save ==========

    /**
     * Saves a partial settings update to the project settings file.
     * Only non-null fields in the update are persisted.
     *
     * @param update the settings update to apply
     */
    public void save(SettingsUpdate update) {
        if (projectSettingsPath == null) return;

        try {
            Path parent = projectSettingsPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            withFileLock(projectSettingsPath, () -> {
                // Load existing project settings
                SettingsData existing = loadFromFile(projectSettingsPath);
                // Apply update
                SettingsData updated = applyUpdate(existing, update);
                // Write back
                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(updated);
                Files.writeString(projectSettingsPath, json);
                return null;
            });

            // Reload merged settings
            this.effectiveSettings = loadAndMerge();
        } catch (IOException e) {
            log.error("Failed to save settings to {}", projectSettingsPath, e);
        }
    }

    /**
     * Reloads settings from disk.
     */
    public void reload() {
        this.effectiveSettings = loadAndMerge();
    }

    // ========== Private Helpers ==========

    protected SettingsData loadAndMerge() {
        SettingsData global = globalSettingsPath != null
            ? loadFromFile(globalSettingsPath) : SettingsData.EMPTY;
        SettingsData project = projectSettingsPath != null
            ? loadFromFile(projectSettingsPath) : SettingsData.EMPTY;
        return deepMerge(global, project);
    }

    private SettingsData loadFromFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return SettingsData.EMPTY;
        }
        try {
            String content = Files.readString(path);
            if (content.isBlank()) return SettingsData.EMPTY;
            SettingsData loaded = MAPPER.readValue(content, SettingsData.class);
            return loaded != null ? migrateIfNeeded(loaded) : SettingsData.EMPTY;
        } catch (IOException e) {
            log.warn("Failed to load settings from {}: {}", path, e.getMessage());
            return SettingsData.EMPTY;
        }
    }

    /**
     * Deep-merges two settings objects. Project settings override global settings.
     * For nested objects (compaction, retry, etc.), project fields override global fields.
     */
    static SettingsData deepMerge(SettingsData global, SettingsData project) {
        try {
            JsonNode globalNode = MAPPER.valueToTree(global);
            JsonNode projectNode = MAPPER.valueToTree(project);
            JsonNode merged = mergeNodes(globalNode, projectNode);
            return MAPPER.treeToValue(merged, SettingsData.class);
        } catch (Exception e) {
            log.warn("Failed to deep-merge settings, using project settings", e);
            return project;
        }
    }

    private static JsonNode mergeNodes(JsonNode base, JsonNode override) {
        if (!base.isObject() || !override.isObject()) {
            return override;
        }
        ObjectNode result = (ObjectNode) base.deepCopy();
        override.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode overrideValue = entry.getValue();
            if (!overrideValue.isNull()) {
                JsonNode baseValue = result.get(key);
                if (baseValue != null && baseValue.isObject() && overrideValue.isObject()) {
                    result.set(key, mergeNodes(baseValue, overrideValue));
                } else {
                    result.set(key, overrideValue);
                }
            }
        });
        return result;
    }

    /**
     * Applies a partial update to existing settings data.
     */
    static SettingsData applyUpdate(SettingsData existing, SettingsUpdate update) {
        return new SettingsData(
            update.defaultProvider() != null ? update.defaultProvider() : existing.defaultProvider(),
            update.defaultModel() != null ? update.defaultModel() : existing.defaultModel(),
            update.defaultThinkingLevel() != null ? update.defaultThinkingLevel() : existing.defaultThinkingLevel(),
            update.transport() != null ? update.transport() : existing.transport(),
            update.steeringMode() != null ? update.steeringMode() : existing.steeringMode(),
            update.followUpMode() != null ? update.followUpMode() : existing.followUpMode(),
            update.theme() != null ? update.theme() : existing.theme(),
            update.showImages() != null ? update.showImages() : existing.showImages(),
            update.clearOnShrink() != null ? update.clearOnShrink() : existing.clearOnShrink(),
            update.autoResize() != null ? update.autoResize() : existing.autoResize(),
            update.blockImages() != null ? update.blockImages() : existing.blockImages(),
            update.compaction() != null ? update.compaction() : existing.compaction(),
            update.branchSummary() != null ? update.branchSummary() : existing.branchSummary(),
            update.retry() != null ? update.retry() : existing.retry(),
            update.thinkingBudgets() != null ? update.thinkingBudgets() : existing.thinkingBudgets(),
            update.extensionPaths() != null ? update.extensionPaths() : existing.extensionPaths(),
            update.skillPaths() != null ? update.skillPaths() : existing.skillPaths(),
            update.promptPaths() != null ? update.promptPaths() : existing.promptPaths(),
            update.themePaths() != null ? update.themePaths() : existing.themePaths()
        );
    }

    /**
     * Migrates settings from older formats if needed.
     */
    private SettingsData migrateIfNeeded(SettingsData data) {
        // Currently no migrations needed - placeholder for future migrations
        return data;
    }

    private <T> T withFileLock(Path path, IOSupplier<T> operation) throws IOException {
        if (!Files.exists(path)) {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, "{}");
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            return operation.get();
        }
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }
}
