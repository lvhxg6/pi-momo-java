package com.pi.coding.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.pi.ai.core.types.ImageContent;

import java.util.List;

/**
 * RPC commands received via stdin as JSON lines.
 *
 * <p>Each subtype overrides {@link #type()} to return a constant discriminator
 * string, following the same pattern as {@link com.pi.agent.event.AgentEvent}.
 *
 * <p><b>Validates: Requirements 20.3-20.14</b>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RpcCommand.Prompt.class, name = "prompt"),
    @JsonSubTypes.Type(value = RpcCommand.Steer.class, name = "steer"),
    @JsonSubTypes.Type(value = RpcCommand.FollowUp.class, name = "follow_up"),
    @JsonSubTypes.Type(value = RpcCommand.Abort.class, name = "abort"),
    @JsonSubTypes.Type(value = RpcCommand.NewSession.class, name = "new_session"),
    @JsonSubTypes.Type(value = RpcCommand.GetState.class, name = "get_state"),
    @JsonSubTypes.Type(value = RpcCommand.SetModel.class, name = "set_model"),
    @JsonSubTypes.Type(value = RpcCommand.CycleModel.class, name = "cycle_model"),
    @JsonSubTypes.Type(value = RpcCommand.SetThinkingLevel.class, name = "set_thinking_level"),
    @JsonSubTypes.Type(value = RpcCommand.CycleThinkingLevel.class, name = "cycle_thinking_level"),
    @JsonSubTypes.Type(value = RpcCommand.SetSteeringMode.class, name = "set_steering_mode"),
    @JsonSubTypes.Type(value = RpcCommand.SetFollowUpMode.class, name = "set_follow_up_mode"),
    @JsonSubTypes.Type(value = RpcCommand.Compact.class, name = "compact"),
    @JsonSubTypes.Type(value = RpcCommand.Bash.class, name = "bash"),
    @JsonSubTypes.Type(value = RpcCommand.AbortBash.class, name = "abort_bash"),
    @JsonSubTypes.Type(value = RpcCommand.GetSessionStats.class, name = "get_session_stats"),
    @JsonSubTypes.Type(value = RpcCommand.ExportHtml.class, name = "export_html"),
    @JsonSubTypes.Type(value = RpcCommand.SwitchSession.class, name = "switch_session"),
    @JsonSubTypes.Type(value = RpcCommand.Fork.class, name = "fork"),
    @JsonSubTypes.Type(value = RpcCommand.GetMessages.class, name = "get_messages"),
    @JsonSubTypes.Type(value = RpcCommand.GetCommands.class, name = "get_commands"),
    @JsonSubTypes.Type(value = RpcCommand.SetAutoCompaction.class, name = "set_auto_compaction"),
    @JsonSubTypes.Type(value = RpcCommand.SetAutoRetry.class, name = "set_auto_retry"),
    @JsonSubTypes.Type(value = RpcCommand.AbortRetry.class, name = "abort_retry")
})
public sealed interface RpcCommand {
    String id();
    String type();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Prompt(@JsonProperty("id") String id,
                  @JsonProperty("message") String message,
                  @JsonProperty("images") List<ImageContent> images,
                  @JsonProperty("streamingBehavior") String streamingBehavior) implements RpcCommand {
        @Override public String type() { return "prompt"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Steer(@JsonProperty("id") String id,
                 @JsonProperty("message") String message,
                 @JsonProperty("images") List<ImageContent> images) implements RpcCommand {
        @Override public String type() { return "steer"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FollowUp(@JsonProperty("id") String id,
                    @JsonProperty("message") String message,
                    @JsonProperty("images") List<ImageContent> images) implements RpcCommand {
        @Override public String type() { return "follow_up"; }
    }

    record Abort(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "abort"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NewSession(@JsonProperty("id") String id,
                      @JsonProperty("parentSession") String parentSession) implements RpcCommand {
        @Override public String type() { return "new_session"; }
    }

    record GetState(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "get_state"; }
    }

    record SetModel(@JsonProperty("id") String id,
                    @JsonProperty("provider") String provider,
                    @JsonProperty("modelId") String modelId) implements RpcCommand {
        @Override public String type() { return "set_model"; }
    }

    record CycleModel(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "cycle_model"; }
    }

    record SetThinkingLevel(@JsonProperty("id") String id,
                            @JsonProperty("level") String level) implements RpcCommand {
        @Override public String type() { return "set_thinking_level"; }
    }

    record CycleThinkingLevel(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "cycle_thinking_level"; }
    }

    record SetSteeringMode(@JsonProperty("id") String id,
                           @JsonProperty("mode") String mode) implements RpcCommand {
        @Override public String type() { return "set_steering_mode"; }
    }

    record SetFollowUpMode(@JsonProperty("id") String id,
                           @JsonProperty("mode") String mode) implements RpcCommand {
        @Override public String type() { return "set_follow_up_mode"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Compact(@JsonProperty("id") String id,
                   @JsonProperty("customInstructions") String customInstructions) implements RpcCommand {
        @Override public String type() { return "compact"; }
    }

    record SetAutoCompaction(@JsonProperty("id") String id,
                             @JsonProperty("enabled") boolean enabled) implements RpcCommand {
        @Override public String type() { return "set_auto_compaction"; }
    }

    record SetAutoRetry(@JsonProperty("id") String id,
                        @JsonProperty("enabled") boolean enabled) implements RpcCommand {
        @Override public String type() { return "set_auto_retry"; }
    }

    record AbortRetry(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "abort_retry"; }
    }

    record Bash(@JsonProperty("id") String id,
                @JsonProperty("command") String command) implements RpcCommand {
        @Override public String type() { return "bash"; }
    }

    record AbortBash(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "abort_bash"; }
    }

    record GetSessionStats(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "get_session_stats"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ExportHtml(@JsonProperty("id") String id,
                      @JsonProperty("outputPath") String outputPath) implements RpcCommand {
        @Override public String type() { return "export_html"; }
    }

    record SwitchSession(@JsonProperty("id") String id,
                         @JsonProperty("sessionPath") String sessionPath) implements RpcCommand {
        @Override public String type() { return "switch_session"; }
    }

    record Fork(@JsonProperty("id") String id,
                @JsonProperty("entryId") String entryId) implements RpcCommand {
        @Override public String type() { return "fork"; }
    }

    record GetMessages(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "get_messages"; }
    }

    record GetCommands(@JsonProperty("id") String id) implements RpcCommand {
        @Override public String type() { return "get_commands"; }
    }
}
