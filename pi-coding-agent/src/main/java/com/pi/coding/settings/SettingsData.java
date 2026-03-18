package com.pi.coding.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw settings data structure for JSON serialization/deserialization.
 * All fields are nullable to support partial settings files.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SettingsData(
    @JsonProperty("defaultProvider") String defaultProvider,
    @JsonProperty("defaultModel") String defaultModel,
    @JsonProperty("defaultThinkingLevel") String defaultThinkingLevel,
    @JsonProperty("transport") String transport,
    @JsonProperty("steeringMode") String steeringMode,
    @JsonProperty("followUpMode") String followUpMode,
    @JsonProperty("theme") String theme,
    @JsonProperty("showImages") Boolean showImages,
    @JsonProperty("clearOnShrink") Boolean clearOnShrink,
    @JsonProperty("autoResize") Boolean autoResize,
    @JsonProperty("blockImages") Boolean blockImages,
    @JsonProperty("compaction") CompactionSettings compaction,
    @JsonProperty("branchSummary") BranchSummarySettings branchSummary,
    @JsonProperty("retry") RetrySettings retry,
    @JsonProperty("thinkingBudgets") ThinkingBudgets thinkingBudgets,
    @JsonProperty("extensionPaths") List<String> extensionPaths,
    @JsonProperty("skillPaths") List<String> skillPaths,
    @JsonProperty("promptPaths") List<String> promptPaths,
    @JsonProperty("themePaths") List<String> themePaths
) {
    public static final SettingsData EMPTY = new SettingsData(
        null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null,
        null, null, null, null
    );
}
