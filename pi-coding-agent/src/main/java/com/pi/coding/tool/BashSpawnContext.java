package com.pi.coding.tool;

import java.util.Map;

/**
 * Context for bash command spawning, allowing customization via hooks.
 *
 * @param command The command to execute
 * @param cwd Current working directory
 * @param env Environment variables
 */
public record BashSpawnContext(
    String command,
    String cwd,
    Map<String, String> env
) {
    /**
     * Creates a new context with a modified command.
     */
    public BashSpawnContext withCommand(String newCommand) {
        return new BashSpawnContext(newCommand, cwd, env);
    }

    /**
     * Creates a new context with a modified working directory.
     */
    public BashSpawnContext withCwd(String newCwd) {
        return new BashSpawnContext(command, newCwd, env);
    }

    /**
     * Creates a new context with modified environment variables.
     */
    public BashSpawnContext withEnv(Map<String, String> newEnv) {
        return new BashSpawnContext(command, cwd, newEnv);
    }
}
