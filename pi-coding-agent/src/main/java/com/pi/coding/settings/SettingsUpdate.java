package com.pi.coding.settings;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Partial settings update - only non-null fields will be persisted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SettingsUpdate(
    String defaultProvider,
    String defaultModel,
    String defaultThinkingLevel,
    String transport,
    String steeringMode,
    String followUpMode,
    String theme,
    Boolean showImages,
    Boolean clearOnShrink,
    Boolean autoResize,
    Boolean blockImages,
    CompactionSettings compaction,
    BranchSummarySettings branchSummary,
    RetrySettings retry,
    ThinkingBudgets thinkingBudgets,
    List<String> extensionPaths,
    List<String> skillPaths,
    List<String> promptPaths,
    List<String> themePaths
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String defaultProvider, defaultModel, defaultThinkingLevel;
        private String transport, steeringMode, followUpMode, theme;
        private Boolean showImages, clearOnShrink, autoResize, blockImages;
        private CompactionSettings compaction;
        private BranchSummarySettings branchSummary;
        private RetrySettings retry;
        private ThinkingBudgets thinkingBudgets;
        private List<String> extensionPaths, skillPaths, promptPaths, themePaths;

        public Builder defaultProvider(String v) { this.defaultProvider = v; return this; }
        public Builder defaultModel(String v) { this.defaultModel = v; return this; }
        public Builder defaultThinkingLevel(String v) { this.defaultThinkingLevel = v; return this; }
        public Builder transport(String v) { this.transport = v; return this; }
        public Builder steeringMode(String v) { this.steeringMode = v; return this; }
        public Builder followUpMode(String v) { this.followUpMode = v; return this; }
        public Builder theme(String v) { this.theme = v; return this; }
        public Builder showImages(Boolean v) { this.showImages = v; return this; }
        public Builder clearOnShrink(Boolean v) { this.clearOnShrink = v; return this; }
        public Builder autoResize(Boolean v) { this.autoResize = v; return this; }
        public Builder blockImages(Boolean v) { this.blockImages = v; return this; }
        public Builder compaction(CompactionSettings v) { this.compaction = v; return this; }
        public Builder branchSummary(BranchSummarySettings v) { this.branchSummary = v; return this; }
        public Builder retry(RetrySettings v) { this.retry = v; return this; }
        public Builder thinkingBudgets(ThinkingBudgets v) { this.thinkingBudgets = v; return this; }
        public Builder extensionPaths(List<String> v) { this.extensionPaths = v; return this; }
        public Builder skillPaths(List<String> v) { this.skillPaths = v; return this; }
        public Builder promptPaths(List<String> v) { this.promptPaths = v; return this; }
        public Builder themePaths(List<String> v) { this.themePaths = v; return this; }

        public SettingsUpdate build() {
            return new SettingsUpdate(
                defaultProvider, defaultModel, defaultThinkingLevel,
                transport, steeringMode, followUpMode, theme,
                showImages, clearOnShrink, autoResize, blockImages,
                compaction, branchSummary, retry, thinkingBudgets,
                extensionPaths, skillPaths, promptPaths, themePaths
            );
        }
    }
}
