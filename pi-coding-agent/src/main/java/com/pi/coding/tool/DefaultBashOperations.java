package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Default implementation of BashOperations using local process execution.
 */
public class DefaultBashOperations implements BashOperations {

    private final String cwd;
    private final Map<String, String> env;
    private String commandPrefix;
    private BashSpawnHook spawnHook;

    /**
     * Creates a new DefaultBashOperations with the specified working directory.
     *
     * @param cwd Current working directory
     */
    public DefaultBashOperations(String cwd) {
        this(cwd, System.getenv());
    }

    /**
     * Creates a new DefaultBashOperations with the specified working directory and environment.
     *
     * @param cwd Current working directory
     * @param env Environment variables
     */
    public DefaultBashOperations(String cwd, Map<String, String> env) {
        this.cwd = cwd;
        this.env = env;
    }

    /**
     * Set a command prefix to prepend to all commands.
     *
     * @param prefix Command prefix
     */
    public void setCommandPrefix(String prefix) {
        this.commandPrefix = prefix;
    }

    /**
     * Set a spawn hook for customizing command execution.
     *
     * @param hook Spawn hook
     */
    public void setSpawnHook(BashSpawnHook hook) {
        this.spawnHook = hook;
    }

    @Override
    public CompletableFuture<BashResult> execute(String command, Integer timeout, CancellationSignal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Apply command prefix
                String finalCommand = command;
                if (commandPrefix != null && !commandPrefix.isEmpty()) {
                    finalCommand = commandPrefix + " " + command;
                }

                // Create spawn context
                BashSpawnContext context = new BashSpawnContext(finalCommand, cwd, env);

                // Apply spawn hook if set
                if (spawnHook != null) {
                    context = spawnHook.adjust(context);
                }

                // Build process
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", context.command());
                pb.directory(new java.io.File(context.cwd()));
                pb.environment().putAll(context.env());
                pb.redirectErrorStream(false);

                Process process = pb.start();

                // Read stdout and stderr
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        return reader.lines().collect(Collectors.joining("\n"));
                    } catch (IOException e) {
                        return "";
                    }
                });

                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        return reader.lines().collect(Collectors.joining("\n"));
                    } catch (IOException e) {
                        return "";
                    }
                });

                // Wait for process with optional timeout
                boolean completed;
                if (timeout != null && timeout > 0) {
                    completed = process.waitFor(timeout, TimeUnit.SECONDS);
                    if (!completed) {
                        destroyProcessTree(process);
                        throw new RuntimeException("Command timed out after " + timeout + " seconds");
                    }
                } else {
                    // Poll for completion, checking cancellation signal
                    while (process.isAlive()) {
                        if (signal != null && signal.isCancelled()) {
                            destroyProcessTree(process);
                            throw new RuntimeException("Command cancelled");
                        }
                        try {
                            completed = process.waitFor(100, TimeUnit.MILLISECONDS);
                            if (completed) break;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            destroyProcessTree(process);
                            throw new RuntimeException("Command execution interrupted", e);
                        }
                    }
                    completed = true;
                }

                String stdout = stdoutFuture.join();
                String stderr = stderrFuture.join();
                int exitCode = process.exitValue();

                return new BashResult(stdout, stderr, exitCode);

            } catch (IOException e) {
                throw new RuntimeException("Failed to execute command: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Command execution interrupted", e);
            }
        });
    }

    /**
     * Destroy a process and its entire process tree.
     */
    private void destroyProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
