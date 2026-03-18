package com.pi.coding.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.agent.types.AgentMessage;

/**
 * Message type for bash executions via the ! command.
 *
 * <p><b>Validates: Requirement 23.4</b>
 *
 * @param command           The bash command that was executed
 * @param output            The command output (stdout + stderr)
 * @param exitCode          The exit code (null if process was killed)
 * @param cancelled         Whether the command was cancelled
 * @param truncated         Whether the output was truncated
 * @param fullOutputPath    Path to full output file if truncated
 * @param timestamp         Unix timestamp in milliseconds
 * @param excludeFromContext If true, excluded from LLM context (!! prefix)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BashExecutionMessage(
        @JsonProperty("command") String command,
        @JsonProperty("output") String output,
        @JsonProperty("exitCode") Integer exitCode,
        @JsonProperty("cancelled") boolean cancelled,
        @JsonProperty("truncated") boolean truncated,
        @JsonProperty("fullOutputPath") String fullOutputPath,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("excludeFromContext") Boolean excludeFromContext
) implements AgentMessage {

    @Override
    public String role() {
        return "bashExecution";
    }

    /**
     * Convert this bash execution to user-facing text for LLM context.
     */
    public String toText() {
        StringBuilder text = new StringBuilder();
        text.append("Ran `").append(command).append("`\n");

        if (output != null && !output.isEmpty()) {
            text.append("```\n").append(output).append("\n```");
        } else {
            text.append("(no output)");
        }

        if (cancelled) {
            text.append("\n\n(command cancelled)");
        } else if (exitCode != null && exitCode != 0) {
            text.append("\n\nCommand exited with code ").append(exitCode);
        }

        if (truncated && fullOutputPath != null) {
            text.append("\n\n[Output truncated. Full output: ").append(fullOutputPath).append("]");
        }

        return text.toString();
    }

    @Override
    public String toString() {
        return toText();
    }
}
