package com.pi.coding.session;

import com.pi.ai.core.types.Model;

/**
 * Result of cycling to a new model.
 *
 * @param model         The new model
 * @param thinkingLevel The thinking level after cycling
 * @param message       Optional message about the model change (nullable)
 */
public record ModelCycleResult(
        Model model,
        String thinkingLevel,
        String message
) {
}
