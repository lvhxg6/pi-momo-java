package com.pi.coding.resource;

import com.pi.coding.extension.ExtensionLoader;
import com.pi.coding.extension.ExtensionRunner;
import com.pi.coding.extension.LoadExtensionsResult;
import com.pi.coding.settings.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Default implementation of ResourceLoader.
 * 
 * <p>Loads resources from:
 * <ul>
 *   <li>Global agent directory (agentDir/)</li>
 *   <li>Project-local directory (cwd/.kiro/)</li>
 *   <li>Explicit paths from settings</li>
 *   <li>Extension-provided paths</li>
 * </ul>
 */
public class DefaultResourceLoader implements ResourceLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceLoader.class);
    private static final String[] CONTEXT_FILE_NAMES = {"AGENTS.md", "CLAUDE.md"};
    private static final String SYSTEM_PROMPT_FILE = "SYSTEM.md";
    private static final String APPEND_SYSTEM_PROMPT_FILE = "APPEND_SYSTEM.md";
    
    private final String cwd;
    private final String agentDir;
    private final SettingsManager settingsManager;
    
    // Extension-provided additional paths
    private List<String> additionalExtensionPaths = new ArrayList<>();
    private List<String> additionalSkillPaths = new ArrayList<>();
    private List<String> additionalPromptPaths = new ArrayList<>();
    
    // Cached results
    private volatile LoadExtensionsResult extensionsResult;
    private volatile LoadSkillsResult skillsResult;
    private volatile LoadPromptsResult promptsResult;
    private volatile List<ContextFile> agentsFiles;
    private volatile String systemPrompt;
    private volatile List<String> appendSystemPrompt;
    private volatile List<ResourceDiagnostic> diagnostics;
    
    public DefaultResourceLoader(ResourceLoaderConfig config) {
        this.cwd = config.cwd();
        this.agentDir = config.agentDir();
        this.settingsManager = config.settingsManager();
        
        // Initialize with empty results
        this.extensionsResult = new LoadExtensionsResult(List.of(), List.of());
        this.skillsResult = new LoadSkillsResult(List.of(), List.of());
        this.promptsResult = new LoadPromptsResult(List.of(), List.of());
        this.agentsFiles = List.of();
        this.systemPrompt = null;
        this.appendSystemPrompt = List.of();
        this.diagnostics = List.of();
    }
    
    @Override
    public CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(() -> {
            List<ResourceDiagnostic> allDiagnostics = new ArrayList<>();
            
            // 1. Load skills
            loadSkillsInternal(allDiagnostics);
            
            // 2. Load prompt templates
            loadPromptsInternal(allDiagnostics);
            
            // 3. Load context files (AGENTS.md)
            loadContextFiles();
            
            // 4. Load system prompt
            loadSystemPromptInternal();
            
            // 5. Load append system prompt
            loadAppendSystemPromptInternal();
            
            this.diagnostics = List.copyOf(allDiagnostics);
        });
    }
    
    @Override
    public LoadExtensionsResult getExtensions() {
        return extensionsResult;
    }
    
    /**
     * Set the extensions result (loaded externally by ExtensionLoader).
     */
    public void setExtensions(LoadExtensionsResult result) {
        this.extensionsResult = result;
    }
    
    @Override
    public LoadSkillsResult getSkills() {
        return skillsResult;
    }
    
    @Override
    public LoadPromptsResult getPrompts() {
        return promptsResult;
    }
    
    @Override
    public List<ContextFile> getAgentsFiles() {
        return agentsFiles;
    }
    
    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    @Override
    public List<String> getAppendSystemPrompt() {
        return appendSystemPrompt;
    }
    
    @Override
    public List<ResourceDiagnostic> getDiagnostics() {
        return diagnostics;
    }
    
    @Override
    public void extendResources(ResourceExtensionPaths paths) {
        this.additionalExtensionPaths = mergePaths(
            this.additionalExtensionPaths, paths.extensionPaths()
        );
        this.additionalSkillPaths = mergePaths(
            this.additionalSkillPaths, paths.skillPaths()
        );
        this.additionalPromptPaths = mergePaths(
            this.additionalPromptPaths, paths.promptPaths()
        );
    }
    
    // Internal loading methods
    
    private void loadSkillsInternal(List<ResourceDiagnostic> allDiagnostics) {
        try {
            List<String> skillPaths = new ArrayList<>(settingsManager.getSkillPaths());
            skillPaths.addAll(additionalSkillPaths);
            
            LoadSkillsResult result = Skills.loadSkills(new LoadSkillsOptions(
                cwd, agentDir, skillPaths, true
            ));
            
            this.skillsResult = result;
            allDiagnostics.addAll(result.diagnostics());
        } catch (Exception e) {
            logger.warn("Error loading skills: {}", e.getMessage());
            this.skillsResult = new LoadSkillsResult(List.of(), List.of());
        }
    }
    
    private void loadPromptsInternal(List<ResourceDiagnostic> allDiagnostics) {
        try {
            List<String> promptPaths = new ArrayList<>(settingsManager.getPromptPaths());
            promptPaths.addAll(additionalPromptPaths);
            
            List<PromptTemplate> templates = PromptTemplates.loadPromptTemplates(
                new LoadPromptTemplatesOptions(cwd, agentDir, promptPaths, true)
            );
            
            // Deduplicate prompts
            DedupeResult<PromptTemplate> deduped = dedupePrompts(templates);
            
            this.promptsResult = new LoadPromptsResult(
                deduped.items(), deduped.diagnostics()
            );
            allDiagnostics.addAll(deduped.diagnostics());
        } catch (Exception e) {
            logger.warn("Error loading prompt templates: {}", e.getMessage());
            this.promptsResult = new LoadPromptsResult(List.of(), List.of());
        }
    }
    
    private void loadContextFiles() {
        List<ContextFile> files = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        
        // 1. Global context file
        ContextFile globalContext = loadContextFileFromDir(Paths.get(agentDir));
        if (globalContext != null) {
            files.add(globalContext);
            seenPaths.add(globalContext.path());
        }
        
        // 2. Walk up from cwd to root, collecting context files
        List<ContextFile> ancestorFiles = new ArrayList<>();
        Path currentDir = Paths.get(cwd).toAbsolutePath().normalize();
        Path root = currentDir.getRoot();
        
        while (true) {
            ContextFile contextFile = loadContextFileFromDir(currentDir);
            if (contextFile != null && !seenPaths.contains(contextFile.path())) {
                ancestorFiles.add(0, contextFile); // prepend
                seenPaths.add(contextFile.path());
            }
            
            if (currentDir.equals(root)) break;
            
            Path parent = currentDir.getParent();
            if (parent == null || parent.equals(currentDir)) break;
            currentDir = parent;
        }
        
        files.addAll(ancestorFiles);
        this.agentsFiles = List.copyOf(files);
    }
    
    private ContextFile loadContextFileFromDir(Path dir) {
        for (String filename : CONTEXT_FILE_NAMES) {
            Path filePath = dir.resolve(filename);
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                try {
                    String content = Files.readString(filePath);
                    return new ContextFile(filePath.toString(), content);
                } catch (IOException e) {
                    logger.warn("Could not read {}: {}", filePath, e.getMessage());
                }
            }
        }
        return null;
    }
    
    private void loadSystemPromptInternal() {
        // Look for SYSTEM.md in cwd/.kiro/ and agentDir/
        String prompt = discoverFileContent(SYSTEM_PROMPT_FILE);
        this.systemPrompt = prompt;
    }
    
    private void loadAppendSystemPromptInternal() {
        // Look for APPEND_SYSTEM.md in cwd/.kiro/ and agentDir/
        String content = discoverFileContent(APPEND_SYSTEM_PROMPT_FILE);
        if (content != null && !content.isBlank()) {
            this.appendSystemPrompt = List.of(content);
        } else {
            this.appendSystemPrompt = List.of();
        }
    }
    
    private String discoverFileContent(String filename) {
        // Check project-local first
        Path projectFile = Paths.get(cwd, ".kiro", filename);
        if (Files.exists(projectFile) && Files.isRegularFile(projectFile)) {
            try {
                return Files.readString(projectFile);
            } catch (IOException e) {
                logger.warn("Could not read {}: {}", projectFile, e.getMessage());
            }
        }
        
        // Check global
        Path globalFile = Paths.get(agentDir, filename);
        if (Files.exists(globalFile) && Files.isRegularFile(globalFile)) {
            try {
                return Files.readString(globalFile);
            } catch (IOException e) {
                logger.warn("Could not read {}: {}", globalFile, e.getMessage());
            }
        }
        
        return null;
    }
    
    private DedupeResult<PromptTemplate> dedupePrompts(List<PromptTemplate> prompts) {
        Map<String, PromptTemplate> seen = new LinkedHashMap<>();
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();
        
        for (PromptTemplate prompt : prompts) {
            PromptTemplate existing = seen.get(prompt.name());
            if (existing != null) {
                diagnostics.add(new ResourceDiagnostic(
                    "collision",
                    "prompt template name \"" + prompt.name() + "\" collision",
                    prompt.filePath(),
                    new ResourceCollision(
                        "prompt",
                        prompt.name(),
                        existing.filePath(),
                        prompt.filePath()
                    )
                ));
            } else {
                seen.put(prompt.name(), prompt);
            }
        }
        
        return new DedupeResult<>(new ArrayList<>(seen.values()), diagnostics);
    }
    
    private List<String> mergePaths(List<String> primary, List<String> additional) {
        Set<String> seen = new LinkedHashSet<>(primary);
        seen.addAll(additional);
        return new ArrayList<>(seen);
    }
    
    private record DedupeResult<T>(
        List<T> items,
        List<ResourceDiagnostic> diagnostics
    ) {}
}
