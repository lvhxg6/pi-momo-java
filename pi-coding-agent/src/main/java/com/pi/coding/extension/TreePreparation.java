package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.coding.session.SessionEntry;

import java.util.List;

/**
 * Preparation data for tree navigation.
 *
 * <p>Contains information about the navigation target, entries to summarize,
 * and customization options for the branch summary.
 *
 * @param targetId            target entry ID to navigate to
 * @param oldLeafId           current leaf entry ID (may be null)
 * @param commonAncestorId    common ancestor between old and new leaf (may be null)
 * @param entriesToSummarize  entries that need to be summarized
 * @param userWantsSummary    whether the user requested a summary
 * @param customInstructions  custom instructions for summarization (may be null)
 * @param replaceInstructions if true, customInstructions replaces the default prompt
 * @param label               label to attach to the branch summary entry (may be null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreePreparation(
    @JsonProperty("targetId") String targetId,
    @JsonProperty("oldLeafId") String oldLeafId,
    @JsonProperty("commonAncestorId") String commonAncestorId,
    @JsonProperty("entriesToSummarize") List<SessionEntry> entriesToSummarize,
    @JsonProperty("userWantsSummary") boolean userWantsSummary,
    @JsonProperty("customInstructions") String customInstructions,
    @JsonProperty("replaceInstructions") Boolean replaceInstructions,
    @JsonProperty("label") String label
) { }
