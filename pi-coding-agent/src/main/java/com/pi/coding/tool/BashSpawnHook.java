package com.pi.coding.tool;

/**
 * Hook interface for customizing bash command spawning.
 */
@FunctionalInterface
public interface BashSpawnHook {

    /**
     * Adjust the spawn context before command execution.
     *
     * @param context Original spawn context
     * @return Modified spawn context
     */
    BashSpawnContext adjust(BashSpawnContext context);
}
