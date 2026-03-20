package com.pi.coding.sdk;

import com.pi.agent.Agent;
import com.pi.agent.config.GetApiKeyFunction;
import com.pi.agent.config.StreamFn;
import com.pi.agent.types.AgentTool;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.ThinkingBudgets;
import com.pi.ai.core.types.Transport;
import com.pi.agent.types.AgentOptions;
import com.pi.agent.types.QueueMode;
import com.pi.agent.types.ToolExecutionMode;
import com.pi.coding.auth.AuthStorage;
import com.pi.coding.extension.ExtensionFactory;
import com.pi.coding.extension.ExtensionRunner;
import com.pi.coding.extension.LoadExtensionsResult;
import com.pi.coding.extension.ToolDefinition;
import com.pi.coding.model.CodingModelRegistry;
import com.pi.coding.resource.DefaultResourceLoader;
import com.pi.coding.resource.ResourceLoader;
import com.pi.coding.resource.ResourceLoaderConfig;
import com.pi.coding.session.AgentSession;
import com.pi.coding.session.AgentSessionConfig;
import com.pi.coding.session.ScopedModel;
import com.pi.coding.session.SessionManager;
import com.pi.coding.settings.SettingsManager;
import com.pi.coding.tool.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * SDK entry point for creating and configuring a coding agent session.
 *
 * <p>Use the {@link #builder()} to configure and create an {@link AgentSession}
 * with all required components wired together.
 *
 * <p><b>Validates: Requirements 21.1-21.13</b>
 */
public final class CodingAgentSdk {

    private static final Logger LOG = Logger.getLogger(CodingAgentSdk.class.getName());
    private static final String DEFAULT_AGENT_DIR = resolveDefaultAgentDir();

    private CodingAgentSdk() {}

    // =========================================================================
    // Result
    // =========================================================================

    /**
     * Result of creating an agent session via the SDK.
     *
     * @param session              the created agent session
     * @param extensionsResult     result of loading extensions (may contain errors)
     * @param modelFallbackMessage optional message if the requested model was unavailable
     */
    public record CreateResult(
            AgentSession session,
            LoadExtensionsResult extensionsResult,
            String modelFallbackMessage
    ) {}

    // =========================================================================
    // Builder
    // =========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String cwd;
        private String agentDir;
        private AuthStorage authStorage;
        private CodingModelRegistry modelRegistry;
        private SessionManager sessionManager;
        private SettingsManager settingsManager;
        private ResourceLoader resourceLoader;
        private StreamFn streamFn;
        private Model initialModel;
        private String initialThinkingLevel;
        private List<ScopedModel> scopedModels;
        private List<AgentTool> builtinTools;
        private List<ToolDefinition<?>> customTools;
        private List<ExtensionFactory> extensionFactories;
        private List<String> initialActiveToolNames;
        private Transport transport;
        private ThinkingBudgets thinkingBudgets;

        Builder() {}

        /** Current working directory. Defaults to {@code System.getProperty("user.dir")}. */
        public Builder cwd(String cwd) { this.cwd = cwd; return this; }

        /** Agent configuration directory. Defaults to {@code ~/.pi}. */
        public Builder agentDir(String agentDir) { this.agentDir = agentDir; return this; }

        /** Auth storage for credential management. Created automatically if not set. */
        public Builder authStorage(AuthStorage authStorage) { this.authStorage = authStorage; return this; }

        /** Model registry. Created automatically from authStorage if not set. */
        public Builder modelRegistry(CodingModelRegistry modelRegistry) { this.modelRegistry = modelRegistry; return this; }

        /** Session manager. Created automatically if not set. */
        public Builder sessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; return this; }

        /** Settings manager. Created automatically if not set. */
        public Builder settingsManager(SettingsManager settingsManager) { this.settingsManager = settingsManager; return this; }

        /** Resource loader. Created automatically if not set. */
        public Builder resourceLoader(ResourceLoader resourceLoader) { this.resourceLoader = resourceLoader; return this; }

        /** Stream function for LLM calls. Required. */
        public Builder streamFn(StreamFn streamFn) { this.streamFn = streamFn; return this; }

        /** Initial model to use. Falls back to first available if not set or unavailable. */
        public Builder initialModel(Model model) { this.initialModel = model; return this; }

        /** Initial thinking level (e.g. "none", "low", "medium", "high"). */
        public Builder initialThinkingLevel(String level) { this.initialThinkingLevel = level; return this; }

        /** Models available for cycling. */
        public Builder scopedModels(List<ScopedModel> models) { this.scopedModels = models; return this; }

        /** Built-in tools. Defaults to {@link #createCodingTools(String)}. */
        public Builder builtinTools(List<AgentTool> tools) { this.builtinTools = tools; return this; }

        /** Custom tool definitions from extensions. */
        public Builder customTools(List<ToolDefinition<?>> tools) { this.customTools = tools; return this; }

        /** Extension factories to load. */
        public Builder extensionFactories(List<ExtensionFactory> factories) { this.extensionFactories = factories; return this; }

        /** Initial set of active tool names. */
        public Builder initialActiveToolNames(List<String> names) { this.initialActiveToolNames = names; return this; }

        /** Transport configuration. */
        public Builder transport(Transport transport) { this.transport = transport; return this; }

        /** Thinking budgets configuration. */
        public Builder thinkingBudgets(ThinkingBudgets budgets) { this.thinkingBudgets = budgets; return this; }

        /**
         * Build and return the configured agent session.
         *
         * @return the creation result containing the session and metadata
         */
        public CreateResult build() {
            // Resolve defaults
            String effectiveCwd = cwd != null ? cwd : System.getProperty("user.dir");
            String effectiveAgentDir = agentDir != null ? agentDir : DEFAULT_AGENT_DIR;

            // Auth
            AuthStorage effectiveAuth = authStorage != null
                    ? authStorage : AuthStorage.inMemory();

            // Model registry
            CodingModelRegistry effectiveRegistry = modelRegistry != null
                    ? modelRegistry : new CodingModelRegistry(effectiveAuth);

            // Settings
            SettingsManager effectiveSettings = settingsManager != null
                    ? settingsManager : SettingsManager.inMemory();

            // Session manager
            SessionManager effectiveSessionManager = sessionManager != null
                    ? sessionManager : SessionManager.inMemory(effectiveCwd);

            // Resource loader
            ResourceLoader effectiveResourceLoader = resourceLoader;
            if (effectiveResourceLoader == null) {
                effectiveResourceLoader = new DefaultResourceLoader(
                        new ResourceLoaderConfig(effectiveCwd, effectiveAgentDir, effectiveSettings));
            }

            // Tools
            List<AgentTool> tools = builtinTools != null
                    ? new ArrayList<>(builtinTools)
                    : new ArrayList<>(createCodingTools(effectiveCwd));

            // Resolve model
            String modelFallbackMessage = null;
            Model resolvedModel = initialModel;
            if (resolvedModel == null) {
                // Try to restore from existing session
                var ctx = effectiveSessionManager.buildSessionContext();
                if (ctx != null && ctx.model() != null) {
                    resolvedModel = effectiveRegistry.find(
                            ctx.model().provider(), ctx.model().modelId());
                }
            }
            if (resolvedModel == null) {
                // Fall back to first available model
                List<Model> available = effectiveRegistry.getAvailableModels();
                if (!available.isEmpty()) {
                    resolvedModel = available.get(0);
                    if (initialModel != null) {
                        modelFallbackMessage = "Requested model " + initialModel.id()
                                + " not available, using " + resolvedModel.id();
                    }
                }
            }

            // Resolve thinking level
            String resolvedThinkingLevel = initialThinkingLevel;
            if (resolvedThinkingLevel == null) {
                var ctx = effectiveSessionManager.buildSessionContext();
                resolvedThinkingLevel = ctx != null && ctx.thinkingLevel() != null
                        ? ctx.thinkingLevel() : "none";
            }

            // GetApiKey wired to model registry
            GetApiKeyFunction getApiKey = effectiveRegistry::getApiKeyForProvider;

            // Build Agent
            AgentOptions agentOpts = AgentOptions.builder()
                    .streamFn(streamFn)
                    .getApiKey(getApiKey)
                    .transport(transport)
                    .thinkingBudgets(thinkingBudgets != null
                            ? thinkingBudgets : convertBudgets(effectiveSettings.getThinkingBudgets()))
                    .toolExecution(ToolExecutionMode.PARALLEL)
                    .steeringMode(QueueMode.ALL)
                    .followUpMode(QueueMode.ONE_AT_A_TIME)
                    .build();

            Agent agent = new Agent(agentOpts);
            agent.setTools(tools);
            if (resolvedModel != null) {
                agent.setModel(resolvedModel);
            }

            // Load extensions
            ExtensionRunner extensionRunner = new ExtensionRunner();
            LoadExtensionsResult extResult = new LoadExtensionsResult(List.of(), List.of());
            if (extensionFactories != null && !extensionFactories.isEmpty()) {
                extResult = extensionRunner.loadExtensions(extensionFactories);
            }

            // Build AgentSession
            @SuppressWarnings("unchecked")
            List<ToolDefinition<?>> effectiveCustomTools = customTools != null
                    ? customTools : List.of();

            AgentSessionConfig config = new AgentSessionConfig(
                    agent,
                    effectiveSessionManager,
                    effectiveSettings,
                    effectiveCwd,
                    scopedModels != null ? scopedModels : List.of(),
                    effectiveResourceLoader,
                    (List) effectiveCustomTools,
                    effectiveRegistry,
                    initialActiveToolNames != null ? initialActiveToolNames : List.of()
            );

            AgentSession session = new AgentSession(config);
            session.setExtensionRunner(extensionRunner);

            // Apply resolved thinking level
            session.setThinkingLevel(resolvedThinkingLevel);

            // Start watching for resource changes (hot-reload)
            effectiveResourceLoader.startWatching();

            return new CreateResult(session, extResult, modelFallbackMessage);
        }
    }

    // =========================================================================
    // Tool Factories (Requirements 21.12)
    // =========================================================================

    /** Create a ReadTool for the given cwd. */
    public static ReadTool createReadTool(String cwd) { return new ReadTool(cwd); }

    /** Create a BashTool for the given cwd. */
    public static BashTool createBashTool(String cwd) { return new BashTool(cwd); }

    /** Create an EditTool for the given cwd. */
    public static EditTool createEditTool(String cwd) { return new EditTool(cwd); }

    /** Create a WriteTool for the given cwd. */
    public static WriteTool createWriteTool(String cwd) { return new WriteTool(cwd); }

    /** Create a GrepTool for the given cwd. */
    public static GrepTool createGrepTool(String cwd) { return new GrepTool(cwd); }

    /** Create a FindTool for the given cwd. */
    public static FindTool createFindTool(String cwd) { return new FindTool(cwd); }

    /** Create an LsTool for the given cwd. */
    public static LsTool createLsTool(String cwd) { return new LsTool(cwd); }

    /**
     * Create the full set of coding tools (read, write, edit, bash, grep, find, ls).
     *
     * @param cwd current working directory
     * @return list of all coding tools
     */
    public static List<AgentTool> createCodingTools(String cwd) {
        return List.of(
                createReadTool(cwd),
                createWriteTool(cwd),
                createEditTool(cwd),
                createBashTool(cwd),
                createGrepTool(cwd),
                createFindTool(cwd),
                createLsTool(cwd)
        );
    }

    /**
     * Create a read-only subset of tools (read, grep, find, ls).
     *
     * @param cwd current working directory
     * @return list of read-only tools
     */
    public static List<AgentTool> createReadOnlyTools(String cwd) {
        return List.of(
                createReadTool(cwd),
                createGrepTool(cwd),
                createFindTool(cwd),
                createLsTool(cwd)
        );
    }

    // =========================================================================
    // Pre-built Tool Constants (Requirements 21.13)
    // =========================================================================

    /** Pre-built ReadTool using the current working directory. */
    public static final ReadTool READ_TOOL = createReadTool(System.getProperty("user.dir"));

    /** Pre-built BashTool using the current working directory. */
    public static final BashTool BASH_TOOL = createBashTool(System.getProperty("user.dir"));

    /** Pre-built EditTool using the current working directory. */
    public static final EditTool EDIT_TOOL = createEditTool(System.getProperty("user.dir"));

    /** Pre-built WriteTool using the current working directory. */
    public static final WriteTool WRITE_TOOL = createWriteTool(System.getProperty("user.dir"));

    /** Pre-built GrepTool using the current working directory. */
    public static final GrepTool GREP_TOOL = createGrepTool(System.getProperty("user.dir"));

    /** Pre-built FindTool using the current working directory. */
    public static final FindTool FIND_TOOL = createFindTool(System.getProperty("user.dir"));

    /** Pre-built LsTool using the current working directory. */
    public static final LsTool LS_TOOL = createLsTool(System.getProperty("user.dir"));

    /** Pre-built full coding tool set using the current working directory. */
    public static final List<AgentTool> CODING_TOOLS = createCodingTools(System.getProperty("user.dir"));

    /** Pre-built read-only tool set using the current working directory. */
    public static final List<AgentTool> READ_ONLY_TOOLS = createReadOnlyTools(System.getProperty("user.dir"));

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Convert coding-agent ThinkingBudgets to pi-ai-core ThinkingBudgets.
     */
    private static ThinkingBudgets convertBudgets(com.pi.coding.settings.ThinkingBudgets src) {
        if (src == null) return null;
        return new ThinkingBudgets(null, src.low(), src.medium(), src.high());
    }

    private static String resolveDefaultAgentDir() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".pi").toString();
    }
}
