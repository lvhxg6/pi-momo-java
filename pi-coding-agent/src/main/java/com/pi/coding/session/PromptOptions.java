package com.pi.coding.session;

import com.pi.ai.core.types.ImageContent;

import java.util.List;

/**
 * Options for the prompt method.
 *
 * @param expandPromptTemplates Whether to expand prompt templates
 * @param images                Optional images to include
 * @param streamingBehavior     "steer" or "followUp" (nullable)
 * @param source                Source identifier (nullable)
 */
public record PromptOptions(
        boolean expandPromptTemplates,
        List<ImageContent> images,
        String streamingBehavior,
        String source
) {
    public static PromptOptions defaults() {
        return new PromptOptions(true, List.of(), null, null);
    }

    public static PromptOptions withImages(List<ImageContent> images) {
        return new PromptOptions(true, images, null, null);
    }
}
