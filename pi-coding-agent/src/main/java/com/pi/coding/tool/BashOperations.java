package com.pi.coding.tool;

import com.pi.ai.core.types.CancellationSignal;

import java.util.concurrent.CompletableFuture;

/**
 * Operations interface for bash tool, allowing custom implementations
 * for remote or sandboxed command execution.
 */
public interface BashOperations {

    /**
     * Execute a bash command.
     *
     * @param command Command to execute
     * @param timeout Timeout in seconds, or null for no timeout
     * @param signal Cancellation signal
     * @return Execution result
     */
    CompletableFuture<BashResult> execute(String command, Integer timeout, CancellationSignal signal);

    /**
     * Result of a bash command execution.
     */
    record BashResult(
        String stdout,
        String stderr,
        int exitCode
    ) {
        /**
         * Get combined output (stdout + stderr).
         */
        public String combinedOutput() {
            StringBuilder sb = new StringBuilder();
            if (stdout != null && !stdout.isEmpty()) {
                sb.append(stdout);
            }
            if (stderr != null && !stderr.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(stderr);
            }
            return sb.toString();
        }
    }
}
