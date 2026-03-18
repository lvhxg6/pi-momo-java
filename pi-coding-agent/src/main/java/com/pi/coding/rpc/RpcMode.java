package com.pi.coding.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pi.ai.core.util.PiAiJson;
import com.pi.coding.session.AgentSession;
import com.pi.coding.session.ModelCycleResult;
import com.pi.coding.session.NewSessionOptions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RPC mode: Headless operation with JSON stdin/stdout protocol.
 *
 * <p>Used for embedding the agent in other applications.
 * Receives commands as JSON lines on stdin, outputs events and responses
 * as JSON lines on stdout.
 *
 * <p>Protocol:
 * <ul>
 *   <li>Commands: JSON objects with {@code type} field, optional {@code id} for correlation</li>
 *   <li>Responses: JSON objects with {@code type: "response"}, {@code command}, {@code success},
 *       and optional {@code data}/{@code error}</li>
 *   <li>Events: AgentSessionEvent objects streamed as they occur</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 20.1-20.17</b>
 */
public class RpcMode {

    private static final Logger LOG = Logger.getLogger(RpcMode.class.getName());

    private final AgentSession session;
    private final BufferedReader reader;
    private final OutputStream outputStream;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Runnable eventUnsubscribe;

    /**
     * Creates a new RPC mode handler.
     *
     * @param session the agent session to control
     * @param stdin   input stream for receiving JSON line commands
     * @param stdout  output stream for sending JSON line responses and events
     */
    public RpcMode(AgentSession session, InputStream stdin, OutputStream stdout) {
        this.session = session;
        this.reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
        this.outputStream = stdout;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Start the RPC loop. Blocks the calling thread until {@link #stop()} is called
     * or the input stream is closed.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("RPC mode already running");
        }

        // Subscribe to agent session events and forward them as JSON lines
        eventUnsubscribe = session.subscribe(event -> {
            String eventType = event.getClass().getSimpleName();
            // Convert camelCase class name to snake_case event type
            String snakeType = camelToSnake(eventType);
            emitEvent(snakeType, event);
        });

        // Read JSON lines from stdin
        try {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                handleLine(line);
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.log(Level.WARNING, "Error reading from stdin", e);
            }
        } finally {
            running.set(false);
            if (eventUnsubscribe != null) {
                eventUnsubscribe.run();
                eventUnsubscribe = null;
            }
        }
    }

    /**
     * Stop the RPC loop.
     */
    public void stop() {
        running.set(false);
        try {
            reader.close();
        } catch (IOException e) {
            // Ignore close errors
        }
    }

    // =========================================================================
    // Line Processing
    // =========================================================================

    /**
     * Process a single JSON line from stdin.
     */
    private void handleLine(String line) {
        try {
            RpcCommand command = PiAiJson.MAPPER.readValue(line, RpcCommand.class);
            RpcResponse response = handleCommand(command);
            emitResponse(response);
        } catch (JsonProcessingException e) {
            emitResponse(RpcResponse.error(null, "parse",
                    "Failed to parse command: " + e.getMessage()));
        }
    }

    // =========================================================================
    // Command Dispatch
    // =========================================================================

    /**
     * Handle a single RPC command and return the response.
     *
     * @param command the parsed command
     * @return the response to send back
     */
    RpcResponse handleCommand(RpcCommand command) {
        String id = command.id();
        String type = command.type();

        try {
            // Prompting
            if (command instanceof RpcCommand.Prompt cmd) return handlePrompt(id, cmd);
            if (command instanceof RpcCommand.Steer cmd) return handleSteer(id, cmd);
            if (command instanceof RpcCommand.FollowUp cmd) return handleFollowUp(id, cmd);
            if (command instanceof RpcCommand.Abort) return handleAbort(id);

            // Session
            if (command instanceof RpcCommand.NewSession cmd) return handleNewSession(id, cmd);
            if (command instanceof RpcCommand.GetState) return handleGetState(id);

            // Model
            if (command instanceof RpcCommand.SetModel cmd) return handleSetModel(id, cmd);
            if (command instanceof RpcCommand.CycleModel) return handleCycleModel(id);

            // Thinking
            if (command instanceof RpcCommand.SetThinkingLevel cmd) return handleSetThinkingLevel(id, cmd);
            if (command instanceof RpcCommand.CycleThinkingLevel) return handleCycleThinkingLevel(id);

            // Queue Modes
            if (command instanceof RpcCommand.SetSteeringMode cmd) return handleSetSteeringMode(id, cmd);
            if (command instanceof RpcCommand.SetFollowUpMode cmd) return handleSetFollowUpMode(id, cmd);

            // Compaction
            if (command instanceof RpcCommand.Compact cmd) return handleCompact(id, cmd);
            if (command instanceof RpcCommand.SetAutoCompaction cmd) return handleSetAutoCompaction(id, cmd);

            // Retry
            if (command instanceof RpcCommand.SetAutoRetry cmd) return handleSetAutoRetry(id, cmd);
            if (command instanceof RpcCommand.AbortRetry) return handleAbortRetry(id);

            // Bash
            if (command instanceof RpcCommand.Bash cmd) return handleBash(id, cmd);
            if (command instanceof RpcCommand.AbortBash) return handleAbortBash(id);

            // Session Management
            if (command instanceof RpcCommand.GetSessionStats) return handleGetSessionStats(id);
            if (command instanceof RpcCommand.ExportHtml cmd) return handleExportHtml(id, cmd);
            if (command instanceof RpcCommand.SwitchSession cmd) return handleSwitchSession(id, cmd);
            if (command instanceof RpcCommand.Fork cmd) return handleFork(id, cmd);

            // Messages & Commands
            if (command instanceof RpcCommand.GetMessages) return handleGetMessages(id);
            if (command instanceof RpcCommand.GetCommands) return handleGetCommands(id);

            return RpcResponse.error(id, type, "Unknown command type: " + type);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error handling command: " + type, e);
            return RpcResponse.error(id, type, e.getMessage());
        }
    }

    // =========================================================================
    // Command Handlers
    // =========================================================================

    private RpcResponse handlePrompt(String id, RpcCommand.Prompt cmd) {
        // Don't block - events will stream asynchronously
        session.prompt(cmd.message(), null)
                .exceptionally(e -> {
                    emitResponse(RpcResponse.error(id, "prompt", e.getMessage()));
                    return null;
                });
        return RpcResponse.success(id, "prompt");
    }

    private RpcResponse handleSteer(String id, RpcCommand.Steer cmd) {
        session.steer(cmd.message());
        return RpcResponse.success(id, "steer");
    }

    private RpcResponse handleFollowUp(String id, RpcCommand.FollowUp cmd) {
        session.followUp(cmd.message());
        return RpcResponse.success(id, "follow_up");
    }

    private RpcResponse handleAbort(String id) {
        session.abort();
        return RpcResponse.success(id, "abort");
    }

    private RpcResponse handleNewSession(String id, RpcCommand.NewSession cmd) {
        // Create a new session via session manager
        NewSessionOptions opts = cmd.parentSession() != null
                ? NewSessionOptions.withParent(cmd.parentSession())
                : null;
        session.getSessionManager().newSession(opts);
        return RpcResponse.success(id, "new_session",
                Map.of("cancelled", false));
    }

    private RpcResponse handleGetState(String id) {
        return RpcResponse.success(id, "get_state", Map.of(
                "model", session.getModel() != null ? session.getModel() : "unknown",
                "thinkingLevel", session.getThinkingLevel(),
                "isStreaming", session.isStreaming(),
                "isCompacting", session.isCompacting(),
                "steeringMode", session.getSteeringMode(),
                "followUpMode", session.getFollowUpMode(),
                "autoCompactionEnabled", session.isAutoCompactionEnabled(),
                "messageCount", session.getMessages().size()
        ));
    }

    private RpcResponse handleSetModel(String id, RpcCommand.SetModel cmd) {
        var registry = session.getModelRegistry();
        var model = registry.find(cmd.provider(), cmd.modelId());
        if (model == null) {
            return RpcResponse.error(id, "set_model",
                    "Model not found: " + cmd.provider() + "/" + cmd.modelId());
        }
        session.setModel(model);
        return RpcResponse.success(id, "set_model", model);
    }

    private RpcResponse handleCycleModel(String id) {
        ModelCycleResult result = session.cycleModel();
        return RpcResponse.success(id, "cycle_model", result);
    }

    private RpcResponse handleSetThinkingLevel(String id, RpcCommand.SetThinkingLevel cmd) {
        session.setThinkingLevel(cmd.level());
        return RpcResponse.success(id, "set_thinking_level");
    }

    private RpcResponse handleCycleThinkingLevel(String id) {
        String level = session.cycleThinkingLevel();
        return RpcResponse.success(id, "cycle_thinking_level",
                Map.of("level", level));
    }

    private RpcResponse handleSetSteeringMode(String id, RpcCommand.SetSteeringMode cmd) {
        session.setSteeringMode(cmd.mode());
        return RpcResponse.success(id, "set_steering_mode");
    }

    private RpcResponse handleSetFollowUpMode(String id, RpcCommand.SetFollowUpMode cmd) {
        session.setFollowUpMode(cmd.mode());
        return RpcResponse.success(id, "set_follow_up_mode");
    }

    private RpcResponse handleCompact(String id, RpcCommand.Compact cmd) {
        try {
            var result = session.compact(cmd.customInstructions()).get();
            return RpcResponse.success(id, "compact", result);
        } catch (Exception e) {
            return RpcResponse.error(id, "compact", e.getMessage());
        }
    }

    private RpcResponse handleSetAutoCompaction(String id, RpcCommand.SetAutoCompaction cmd) {
        session.setAutoCompactionEnabled(cmd.enabled());
        return RpcResponse.success(id, "set_auto_compaction");
    }

    private RpcResponse handleSetAutoRetry(String id, RpcCommand.SetAutoRetry cmd) {
        session.setAutoRetryEnabled(cmd.enabled());
        return RpcResponse.success(id, "set_auto_retry");
    }

    private RpcResponse handleAbortRetry(String id) {
        session.abortRetry();
        return RpcResponse.success(id, "abort_retry");
    }

    private RpcResponse handleBash(String id, RpcCommand.Bash cmd) {
        try {
            var result = session.executeBash(cmd.command(), false).get();
            return RpcResponse.success(id, "bash", result);
        } catch (Exception e) {
            return RpcResponse.error(id, "bash", e.getMessage());
        }
    }

    private RpcResponse handleAbortBash(String id) {
        session.abortBash();
        return RpcResponse.success(id, "abort_bash");
    }

    private RpcResponse handleGetSessionStats(String id) {
        return RpcResponse.success(id, "get_session_stats", Map.of(
                "messageCount", session.getMessages().size(),
                "isStreaming", session.isStreaming(),
                "isCompacting", session.isCompacting(),
                "retryAttempt", session.getRetryAttempt()
        ));
    }

    private RpcResponse handleExportHtml(String id, RpcCommand.ExportHtml cmd) {
        String html = session.exportToHtml();
        return RpcResponse.success(id, "export_html", Map.of("html", html));
    }

    private RpcResponse handleSwitchSession(String id, RpcCommand.SwitchSession cmd) {
        try {
            session.switchSession(cmd.sessionPath());
            return RpcResponse.success(id, "switch_session",
                    Map.of("cancelled", false));
        } catch (Exception e) {
            return RpcResponse.error(id, "switch_session", e.getMessage());
        }
    }

    private RpcResponse handleFork(String id, RpcCommand.Fork cmd) {
        try {
            String leafId = session.fork(cmd.entryId());
            return RpcResponse.success(id, "fork",
                    Map.of("leafId", leafId, "cancelled", false));
        } catch (Exception e) {
            return RpcResponse.error(id, "fork", e.getMessage());
        }
    }

    private RpcResponse handleGetMessages(String id) {
        return RpcResponse.success(id, "get_messages",
                Map.of("messages", session.getMessages()));
    }

    private RpcResponse handleGetCommands(String id) {
        // Return available commands (prompt templates, skills, extension commands)
        var skills = session.getResourceLoader() != null
                && session.getResourceLoader().getSkills() != null
                ? session.getResourceLoader().getSkills().skills() : List.of();

        var prompts = session.getResourceLoader() != null
                && session.getResourceLoader().getPrompts() != null
                ? session.getResourceLoader().getPrompts().prompts() : List.of();

        return RpcResponse.success(id, "get_commands",
                Map.of("skills", skills, "prompts", prompts));
    }

    // =========================================================================
    // Output Helpers
    // =========================================================================

    /**
     * Write a JSON line response to stdout.
     */
    private synchronized void emitResponse(RpcResponse response) {
        writeJsonLine(response);
    }

    /**
     * Write a JSON line event to stdout.
     */
    private synchronized void emitEvent(String eventType, Object data) {
        writeJsonLine(RpcEvent.of(eventType, data));
    }

    /**
     * Write an object as a JSON line to the output stream.
     */
    private void writeJsonLine(Object obj) {
        try {
            String json = PiAiJson.MAPPER.writeValueAsString(obj);
            byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error writing JSON line to stdout", e);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Convert a CamelCase class name to snake_case event type.
     * e.g. "AutoCompactionStartEvent" → "auto_compaction_start_event"
     */
    static String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) return camelCase;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
