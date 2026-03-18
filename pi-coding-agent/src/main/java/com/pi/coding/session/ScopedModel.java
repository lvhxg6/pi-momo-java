package com.pi.coding.session;

import com.pi.ai.core.types.Model;

/**
 * A model scoped for cycling, with an optional thinking level override.
 *
 * @param model         The model
 * @param thinkingLevel Optional thinking level override (nullable)
 */
public record ScopedModel(
        Model model,
        String thinkingLevel
) {
    public ScopedModel(Model model) {
        this(model, null);
    }
}
