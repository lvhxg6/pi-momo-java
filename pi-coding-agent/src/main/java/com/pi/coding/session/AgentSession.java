package com.pi.coding.session;

import com.pi.agent.Agent;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.*;
import com.pi.ai.core.types.*;
import com.pi.coding.compaction.Compaction;
import com.pi.coding.compaction.CompactionResult;
import com.pi.coding.compaction.CompactionUtils;
import com.pi.coding.extension.ExtensionRunner;
import com.pi.coding.extension.ToolDefinition;
import com.pi.coding.extension.ToolInfo;
import com.pi.coding.message.*;
import com.pi.coding.model.CodingModelRegistry;
import com.pi.coding.prompt.SystemPromptBuilder;
import com.pi.coding.prompt.SystemPromptConfig;
import com.pi.coding.resource.ContextFile;
import com.pi.coding.resource.ResourceChangeEvent;
import com.pi.coding.resource.ResourceChangeListener;
import com.pi.coding.resource.ResourceLoader;
import com.pi.coding.resource.Skill;
import com.pi.coding.settings.RetrySettings;
import com.pi.coding.settings.SettingsManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level agent session that wraps the pi-agent-core Agent with
 * coding-agent specific features: session persistence, auto-compaction,
 * auto-retry, model cycling, bash execution, and extension integration.
 *
 * <p><b>Validates: Requirements 2.1-2.18</b>
 */
public class AgentSession {

    private static final Logger LOG = Logger.getLogger(AgentSession.class.getName());

    // ---- Configuration ----
    private final Agent agent;
    private final SessionManager sessionManager;
    private final SettingsManager settingsManager;
    private final String cwd;
    private final CodingModelRegistry modelRegistry;
    private final ResourceLoader resourceLoader;

    // ---- State ----
    private volatile Model currentModel;
    private volatile String currentThinkingLevel;
    private volatile boolean isCompacting;
    private volatile boolean autoCompactionEnabled = true;
    private volatile boolean autoRetryEnabled = true;
    private volatile String steeringMode = "all";
    private volatile String followUpMode = "all";
    private volatile ExtensionRunner extensionRunner;
    private List<ScopedModel> scopedModels;
    private List<String> activeToolNames;
    private final List<ToolDefinition> customTools;

    // ---- Event listeners ----
    private final CopyOnWriteArrayList<Consumer<AgentSessionEvent>> listeners = new CopyOnWriteArrayList<>();
    private Runnable agentUnsubscribe;
    private final ResourceChangeListener resourceChangeListener;

    // ---- Retry state ----
    private final AtomicInteger retryAttempt = new AtomicInteger(0);
    private volatile CompletableFuture<Void> retryFuture;

    // ---- Compaction state ----
    private volatile CompletableFuture<CompactionResult> compactionFuture;

    // ---- Bash state ----
    private volatile Process currentBashProcess;
    private final AtomicBoolean bashRunning = new AtomicBoolean(false);
    private final List<BashExecutionMessage> pendingBashMessages =
            Collections.synchronizedList(new ArrayList<>());

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Creates a new AgentSession with the given configuration.
     *
     * @param config the session configuration
     */
    public AgentSession(AgentSessionConfig config) {
        this.agent = config.agent();
        this.sessionManager = config.sessionManager();
        this.settingsManager = config.settingsManager();
        this.cwd = config.cwd();
        this.modelRegistry = config.modelRegistry();
        this.resourceLoader = config.resourceLoader();
        this.scopedModels = new ArrayList<>(config.scopedModels());
        this.customTools = new ArrayList<>(config.customTools());
        this.activeToolNames = new ArrayList<>(config.initialActiveToolNames());

        // Initialize model and thinking level from agent state
        AgentState state = agent.getState();
        this.currentModel = state.getModel();
        this.currentThinkingLevel = state.getThinkingLevel() != null
                ? state.getThinkingLevel().name().toLowerCase() : "off";

        // Subscribe to agent events
        this.agentUnsubscribe = agent.subscribe(this::handleAgentEvent);

        // Register resource change listener for hot-reload
        this.resourceChangeListener = this::handleResourceChange;
        if (resourceLoader != null) {
            resourceLoader.addChangeListener(resourceChangeListener);
        }

        // Set up convertToLlm
        agent.getState(); // ensure initialized
    }

    /**
     * Handle resource change events from ResourceLoader.
     * Rebuilds the system prompt when skills or prompts change.
     */
    private void handleResourceChange(ResourceChangeEvent event) {
        LOG.info("Resource change detected, rebuilding system prompt");
        rebuildSystemPrompt();
        emit(new ResourceChangeSessionEvent(event));
    }

    // =========================================================================
    // Event Subscription
    // =========================================================================

    /**
     * Subscribe to agent session events.
     *
     * @param listener the event listener
     * @return a Runnable that unsubscribes the listener when called
     */
    public Runnable subscribe(Consumer<AgentSessionEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * Emit an event to all listeners.
     */
    private void emit(AgentSessionEvent event) {
        for (Consumer<AgentSessionEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error in session event listener", e);
            }
        }
    }

    /**
     * Handle events from the underlying Agent.
     */
    private void handleAgentEvent(AgentEvent event) {
        // Forward as AgentSessionEvent (AgentEvent is also an AgentSessionEvent via wrapper)
        emit(new AgentEventWrapper(event));

        // Persist messages on message_end
        if (event instanceof AgentEvent.MessageEnd me) {
            persistMessage(me.message());
        }

        // Check for auto-compaction on agent_end
        if (event instanceof AgentEvent.AgentEnd ae) {
            handleAgentEnd(ae);
        }
    }

    /**
     * Persist a message to the session manager.
     */
    private void persistMessage(AgentMessage message) {
        try {
            sessionManager.appendMessage(message);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to persist message", e);
        }
    }

    /**
     * Handle agent_end event - check for retryable errors and auto-compaction.
     */
    private void handleAgentEnd(AgentEvent.AgentEnd event) {
        if (event.messages() == null || event.messages().isEmpty()) return;

        // Find last assistant message
        AgentMessage lastMsg = event.messages().get(event.messages().size() - 1);
        if (lastMsg instanceof MessageAdapter adapter
                && adapter.message() instanceof AssistantMessage assistant) {
            // Check for retryable errors
            if (autoRetryEnabled && isRetryableError(assistant)) {
                handleRetryableError(assistant);
                return;
            }
            // Check for auto-compaction
            if (autoCompactionEnabled) {
                checkAutoCompaction(assistant);
            }
        }
    }

    // =========================================================================
    // State Accessors
    // =========================================================================

    public AgentState getState() { return agent.getState(); }
    public Model getModel() { return currentModel; }
    public String getThinkingLevel() { return currentThinkingLevel; }
    public boolean isStreaming() { return agent.getState().isStreaming(); }
    public boolean isCompacting() { return isCompacting; }
    public String getCwd() { return cwd; }
    public SessionManager getSessionManager() { return sessionManager; }
    public SettingsManager getSettingsManager() { return settingsManager; }
    public CodingModelRegistry getModelRegistry() { return modelRegistry; }
    public ResourceLoader getResourceLoader() { return resourceLoader; }
    public String getSteeringMode() { return steeringMode; }
    public String getFollowUpMode() { return followUpMode; }
    public int getRetryAttempt() { return retryAttempt.get(); }
    public boolean isRetrying() { return retryFuture != null && !retryFuture.isDone(); }
    public boolean isAutoRetryEnabled() { return autoRetryEnabled; }
    public boolean isAutoCompactionEnabled() { return autoCompactionEnabled; }
    public List<AgentMessage> getMessages() { return agent.getState().getMessages(); }

    // =========================================================================
    // Model and Thinking Level Management
    // =========================================================================

    /**
     * Set the current model.
     */
    public void setModel(Model model) {
        this.currentModel = model;
        agent.setModel(model);
        sessionManager.appendModelChange(
                model.provider(), model.id());
    }

    /**
     * Cycle to the next model in the scoped models list.
     *
     * @return the cycle result, or null if no models available
     */
    public ModelCycleResult cycleModel() {
        if (scopedModels.isEmpty()) return null;

        int currentIndex = -1;
        for (int i = 0; i < scopedModels.size(); i++) {
            if (scopedModels.get(i).model().equals(currentModel)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % scopedModels.size();
        ScopedModel next = scopedModels.get(nextIndex);

        setModel(next.model());
        if (next.thinkingLevel() != null) {
            setThinkingLevel(next.thinkingLevel());
        }

        return new ModelCycleResult(next.model(), currentThinkingLevel, null);
    }

    /**
     * Set the thinking level.
     */
    public void setThinkingLevel(String level) {
        this.currentThinkingLevel = level;
        AgentThinkingLevel agentLevel = parseThinkingLevel(level);
        agent.setThinkingLevel(agentLevel);
        sessionManager.appendThinkingLevelChange(level);
    }

    /**
     * Cycle to the next thinking level.
     *
     * @return the new thinking level
     */
    public String cycleThinkingLevel() {
        List<String> levels = List.of("off", "low", "medium", "high");
        int idx = levels.indexOf(currentThinkingLevel);
        String next = levels.get((idx + 1) % levels.size());
        setThinkingLevel(next);
        return next;
    }

    private static AgentThinkingLevel parseThinkingLevel(String level) {
        if (level == null) return AgentThinkingLevel.OFF;
        return switch (level.toLowerCase()) {
            case "low" -> AgentThinkingLevel.LOW;
            case "medium" -> AgentThinkingLevel.MEDIUM;
            case "high" -> AgentThinkingLevel.HIGH;
            default -> AgentThinkingLevel.OFF;
        };
    }

    // =========================================================================
    // Prompt and Message Queue
    // =========================================================================

    /**
     * Send a prompt to the agent.
     *
     * @param text    the prompt text
     * @param options prompt options (nullable, uses defaults)
     * @return a future that completes when the agent finishes processing
     */
    public CompletableFuture<Void> prompt(String text, PromptOptions options) {
        if (options == null) options = PromptOptions.defaults();

        // Flush pending bash messages
        flushPendingBashMessages();

        // Rebuild system prompt
        rebuildSystemPrompt();

        return agent.prompt(text);
    }

    /**
     * Send a steering message (interrupt mode).
     */
    public void steer(String text) {
        UserMessage msg = new UserMessage(text, System.currentTimeMillis());
        agent.steer(MessageAdapter.wrap(msg));
    }

    /**
     * Send a follow-up message (wait mode).
     */
    public void followUp(String text) {
        UserMessage msg = new UserMessage(text, System.currentTimeMillis());
        agent.followUp(MessageAdapter.wrap(msg));
    }

    /**
     * Abort the current operation.
     */
    public void abort() {
        agent.abort();
        abortCompaction();
        abortRetry();
    }

    // =========================================================================
    // Compaction
    // =========================================================================

    /**
     * Manually trigger compaction.
     *
     * @param customInstructions optional custom instructions for summary generation
     * @return a future with the compaction result
     */
    public CompletableFuture<CompactionResult> compact(String customInstructions) {
        if (isCompacting) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Compaction already in progress"));
        }

        isCompacting = true;
        CompletableFuture<CompactionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                List<AgentMessage> messages = agent.getState().getMessages();
                int tokenEstimate = CompactionUtils.estimateTokens(messages);
                List<SessionEntry> entries = sessionManager.getEntries();

                // Find cut point
                var cutPoint = Compaction.findCutPoint(
                        entries, 0, entries.size(),
                        settingsManager.getCompactionSettings().keepRecentTokens());

                if (cutPoint.firstKeptEntryIndex() <= 0) {
                    return null;
                }

                // Get the entry ID at the cut point
                String firstKeptEntryId = entries.get(cutPoint.firstKeptEntryIndex()).id();

                // For now, return a basic result
                // Full implementation would call SummaryGenerator
                return CompactionResult.of(
                        "Compaction summary placeholder",
                        firstKeptEntryId,
                        tokenEstimate);
            } finally {
                isCompacting = false;
            }
        });

        this.compactionFuture = future;
        return future;
    }

    /**
     * Abort the current compaction.
     */
    public void abortCompaction() {
        CompletableFuture<CompactionResult> f = compactionFuture;
        if (f != null && !f.isDone()) {
            f.cancel(true);
        }
        isCompacting = false;
    }

    /**
     * Check if auto-compaction should be triggered after an assistant message.
     */
    private void checkAutoCompaction(AssistantMessage assistant) {
        try {
            List<AgentMessage> messages = agent.getState().getMessages();
            int tokenEstimate = CompactionUtils.estimateTokens(messages);
            Model model = currentModel;
            if (model == null) return;

            int contextWindow = model.contextWindow() > 0 ? model.contextWindow() : 200000;
            int reserveTokens = settingsManager.getCompactionSettings().reserveTokens();

            if (CompactionUtils.shouldCompact(tokenEstimate, contextWindow, reserveTokens)) {
                runAutoCompaction("threshold", false);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error checking auto-compaction", e);
        }
    }

    /**
     * Run auto-compaction.
     */
    private void runAutoCompaction(String reason, boolean willRetry) {
        emit(new AutoCompactionStartEvent(reason));
        try {
            CompletableFuture<CompactionResult> future = compact(null);
            CompactionResult result = future.get(120, TimeUnit.SECONDS);
            emit(new AutoCompactionEndEvent(result, false, willRetry, null));
        } catch (CancellationException e) {
            emit(new AutoCompactionEndEvent(null, true, false, null));
        } catch (Exception e) {
            emit(new AutoCompactionEndEvent(null, false, false, e.getMessage()));
        }
    }

    // =========================================================================
    // Auto-Retry
    // =========================================================================

    /**
     * Check if an assistant message represents a retryable error.
     */
    private boolean isRetryableError(AssistantMessage message) {
        if (message.getStopReason() != StopReason.ERROR) return false;
        String errorMsg = message.getErrorMessage();
        if (errorMsg == null) return false;
        String lower = errorMsg.toLowerCase();
        return lower.contains("overloaded")
                || lower.contains("rate_limit")
                || lower.contains("rate limit")
                || lower.contains("server_error")
                || lower.contains("529")
                || lower.contains("503")
                || lower.contains("500");
    }

    /**
     * Handle a retryable error with exponential backoff.
     */
    private void handleRetryableError(AssistantMessage message) {
        RetrySettings retry = settingsManager.getRetrySettings();
        if (!retry.enabled()) return;

        int attempt = retryAttempt.incrementAndGet();
        if (attempt > retry.maxRetries()) {
            retryAttempt.set(0);
            return;
        }

        long delay = Math.min(
                retry.baseDelayMs() * (1L << (attempt - 1)),
                retry.maxDelayMs());

        String reason = message.getErrorMessage() != null ? message.getErrorMessage() : "unknown";
        emit(new AutoRetryStartEvent(attempt, delay, reason));

        retryFuture = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delay);
                agent.continueProcessing();
                emit(new AutoRetryEndEvent(attempt, true, false));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emit(new AutoRetryEndEvent(attempt, false, true));
            } catch (Exception e) {
                emit(new AutoRetryEndEvent(attempt, false, false));
            }
        });
    }

    /**
     * Abort the current retry.
     */
    public void abortRetry() {
        CompletableFuture<Void> f = retryFuture;
        if (f != null && !f.isDone()) {
            f.cancel(true);
        }
        retryAttempt.set(0);
    }

    public void setAutoRetryEnabled(boolean enabled) { this.autoRetryEnabled = enabled; }
    public void setAutoCompactionEnabled(boolean enabled) { this.autoCompactionEnabled = enabled; }

    // =========================================================================
    // Bash Execution
    // =========================================================================

    /**
     * Execute a bash command and record the result.
     *
     * @param command the command to execute
     * @param excludeFromContext if true, result is excluded from LLM context
     * @return a future with the bash execution message
     */
    public CompletableFuture<BashExecutionMessage> executeBash(
            String command, boolean excludeFromContext) {
        bashRunning.set(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.directory(new java.io.File(cwd));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                currentBashProcess = process;

                String output = new String(process.getInputStream().readAllBytes());
                int exitCode = process.waitFor();

                BashExecutionMessage msg = new BashExecutionMessage(
                        command, output, exitCode, false, false, null,
                        System.currentTimeMillis(),
                        excludeFromContext ? true : null);

                pendingBashMessages.add(msg);
                return msg;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new BashExecutionMessage(
                        command, "", null, true, false, null,
                        System.currentTimeMillis(),
                        excludeFromContext ? true : null);
            } catch (Exception e) {
                return new BashExecutionMessage(
                        command, e.getMessage(), 1, false, false, null,
                        System.currentTimeMillis(),
                        excludeFromContext ? true : null);
            } finally {
                bashRunning.set(false);
                currentBashProcess = null;
            }
        });
    }

    /**
     * Abort the current bash execution.
     */
    public void abortBash() {
        Process p = currentBashProcess;
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
    }

    public boolean isBashRunning() { return bashRunning.get(); }

    /**
     * Flush pending bash messages into the agent's message list.
     */
    private void flushPendingBashMessages() {
        synchronized (pendingBashMessages) {
            for (BashExecutionMessage msg : pendingBashMessages) {
                agent.appendMessage(msg);
                sessionManager.appendMessage(msg);
            }
            pendingBashMessages.clear();
        }
    }

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Switch to a different session file.
     */
    public void switchSession(String sessionFile) {
        sessionManager.setSessionFile(java.nio.file.Path.of(sessionFile));
        // Rebuild context from new session
        SessionContext ctx = sessionManager.buildSessionContext();
        agent.replaceMessages(ctx.messages());
        if (ctx.model() != null) {
            // Restore model from session
        }
        if (ctx.thinkingLevel() != null) {
            currentThinkingLevel = ctx.thinkingLevel();
        }
    }

    /**
     * Fork from a specific entry in the session tree.
     *
     * @param fromEntryId the entry to fork from
     * @return the new leaf entry ID
     */
    public String fork(String fromEntryId) {
        sessionManager.setLeaf(fromEntryId);
        SessionContext ctx = sessionManager.buildSessionContext();
        agent.replaceMessages(ctx.messages());
        return sessionManager.getLeafId();
    }

    /**
     * Navigate to a specific entry in the session tree.
     */
    public void navigateTree(String toEntryId, boolean summarize) {
        sessionManager.setLeaf(toEntryId);
        SessionContext ctx = sessionManager.buildSessionContext();
        agent.replaceMessages(ctx.messages());
    }

    // =========================================================================
    // Tool Management
    // =========================================================================

    /**
     * Get the names of currently active tools.
     */
    public List<String> getActiveToolNames() {
        return Collections.unmodifiableList(activeToolNames);
    }

    /**
     * Get information about all available tools.
     */
    public List<ToolInfo> getAllTools() {
        List<ToolInfo> result = new ArrayList<>();
        for (AgentTool tool : agent.getState().getTools()) {
            result.add(new ToolInfo(tool.name(), tool.description(), tool.parameters()));
        }
        return result;
    }

    /**
     * Set the active tools by name.
     */
    public void setActiveToolsByName(List<String> toolNames) {
        this.activeToolNames = new ArrayList<>(toolNames);
        rebuildSystemPrompt();
    }

    /**
     * Set the scoped models for cycling.
     */
    public void setScopedModels(List<ScopedModel> models) {
        this.scopedModels = new ArrayList<>(models);
    }

    public List<ScopedModel> getScopedModels() {
        return Collections.unmodifiableList(scopedModels);
    }

    public void setSteeringMode(String mode) { this.steeringMode = mode; }
    public void setFollowUpMode(String mode) { this.followUpMode = mode; }

    // =========================================================================
    // System Prompt
    // =========================================================================

    /**
     * Rebuild the system prompt based on current state.
     */
    private void rebuildSystemPrompt() {
        try {
            List<Skill> skills = resourceLoader != null && resourceLoader.getSkills() != null
                    ? resourceLoader.getSkills().skills() : List.of();
            List<ContextFile> contextFiles = resourceLoader != null
                    ? resourceLoader.getAgentsFiles() : List.of();
            String customPrompt = resourceLoader != null
                    ? resourceLoader.getSystemPrompt() : null;
            List<String> appendLines = resourceLoader != null
                    ? resourceLoader.getAppendSystemPrompt() : List.of();
            String appendPrompt = appendLines.isEmpty() ? null : String.join("\n", appendLines);

            SystemPromptConfig config = new SystemPromptConfig(
                    cwd, skills, contextFiles, customPrompt, appendPrompt,
                    activeToolNames, Map.of(), List.of());

            String prompt = SystemPromptBuilder.buildSystemPrompt(config);
            agent.setSystemPrompt(prompt);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to rebuild system prompt", e);
        }
    }

    // =========================================================================
    // Extension Integration
    // =========================================================================

    /**
     * Set the extension runner for this session.
     */
    public void setExtensionRunner(ExtensionRunner runner) {
        this.extensionRunner = runner;
    }

    public ExtensionRunner getExtensionRunner() {
        return extensionRunner;
    }

    /**
     * Check if there are extension handlers for a given event type.
     */
    public boolean hasExtensionHandlers(String eventType) {
        ExtensionRunner runner = extensionRunner;
        return runner != null && runner.hasHandlers(eventType);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Dispose of this session, cleaning up resources.
     */
    public void dispose() {
        if (agentUnsubscribe != null) {
            agentUnsubscribe.run();
            agentUnsubscribe = null;
        }
        
        // Remove resource change listener
        if (resourceLoader != null && resourceChangeListener != null) {
            resourceLoader.removeChangeListener(resourceChangeListener);
        }
        
        listeners.clear();
        abortCompaction();
        abortRetry();
        abortBash();
        ExtensionRunner runner = extensionRunner;
        if (runner != null) {
            runner.dispose();
        }
    }

    /**
     * Export the session to HTML.
     *
     * @return the HTML content
     */
    public String exportToHtml() {
        // Basic HTML export
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Session Export</title></head><body>\n");
        for (AgentMessage msg : agent.getState().getMessages()) {
            html.append("<div class=\"message ").append(msg.role()).append("\">\n");
            html.append("<strong>").append(msg.role()).append("</strong>: ");
            html.append(escapeHtml(msg.toString()));
            html.append("\n</div>\n");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // =========================================================================
    // Inner Types
    // =========================================================================

    /**
     * Wrapper that makes an AgentEvent also an AgentSessionEvent.
     */
    public record AgentEventWrapper(AgentEvent event) implements AgentSessionEvent {
    }

    /**
     * Event emitted when resources (skills, prompts) change.
     */
    public record ResourceChangeSessionEvent(ResourceChangeEvent resourceEvent) implements AgentSessionEvent {
    }
}
