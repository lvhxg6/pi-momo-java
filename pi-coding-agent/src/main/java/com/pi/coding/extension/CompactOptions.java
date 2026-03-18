package com.pi.coding.extension;

import com.pi.coding.compaction.CompactionResult;

import java.util.function.Consumer;

/**
 * Options for triggering compaction.
 *
 * @param customInstructions custom instructions for summarization
 * @param onComplete         callback when compaction completes
 * @param onError            callback when compaction fails
 */
public record CompactOptions(
    String customInstructions,
    Consumer<CompactionResult> onComplete,
    Consumer<Exception> onError
) {

    /**
     * Builder for creating CompactOptions instances.
     */
    public static class Builder {
        private String customInstructions;
        private Consumer<CompactionResult> onComplete;
        private Consumer<Exception> onError;

        public Builder customInstructions(String customInstructions) {
            this.customInstructions = customInstructions;
            return this;
        }

        public Builder onComplete(Consumer<CompactionResult> onComplete) {
            this.onComplete = onComplete;
            return this;
        }

        public Builder onError(Consumer<Exception> onError) {
            this.onError = onError;
            return this;
        }

        public CompactOptions build() {
            return new CompactOptions(customInstructions, onComplete, onError);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
