package com.pi.coding.compaction;

/**
 * Result of finding a cut point for compaction.
 *
 * <p><b>Validates: Requirements 3.4, 3.5, 3.6, 3.7</b>
 *
 * @param firstKeptEntryIndex Index of first entry to keep
 * @param turnStartIndex      Index of user message that starts the turn being split, or -1 if not splitting
 * @param isSplitTurn         Whether this cut splits a turn (cut point is not a user message)
 */
public record CutPointResult(
        int firstKeptEntryIndex,
        int turnStartIndex,
        boolean isSplitTurn
) {

    /**
     * Create a result indicating no valid cut point was found.
     */
    public static CutPointResult noValidCutPoint(int startIndex) {
        return new CutPointResult(startIndex, -1, false);
    }
}
