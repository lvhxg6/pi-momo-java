package com.pi.coding.extension;

import com.pi.coding.session.SessionManager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Extended context for command handlers.
 *
 * <p>Includes session control methods only safe in user-initiated commands.
 */
public interface ExtensionCommandContext extends ExtensionContext {

    /**
     * Wait for the agent to finish streaming.
     *
     * @return a future that completes when the agent is idle
     */
    CompletableFuture<Void> waitForIdle();

    /**
     * Start a new session.
     *
     * @return a future that completes with the result
     */
    CompletableFuture<SessionOperationResult> newSession();

    /**
     * Start a new session with options.
     *
     * @param options new session options
     * @return a future that completes with the result
     */
    CompletableFuture<SessionOperationResult> newSession(NewSessionOptions options);

    /**
     * Fork from a specific entry, creating a new session file.
     *
     * @param entryId the entry ID to fork from
     * @return a future that completes with the result
     */
    CompletableFuture<SessionOperationResult> fork(String entryId);

    /**
     * Navigate to a different point in the session tree.
     *
     * @param targetId the target entry ID
     * @return a future that completes with the result
     */
    CompletableFuture<SessionOperationResult> navigateTree(String targetId);

    /**
     * Navigate to a different point in the session tree with options.
     *
     * @param targetId the target entry ID
     * @param options  navigation options
     * @return a future that completes with the result
     */
    CompletableFuture<SessionOperationResult> navigateTree(String targetId, NavigateTreeOptions options);

    /**
     * Switch to a different session file.
     *
     * @param sessionPath the session file path
     * @return a future that completes with the result
     */
    CompletableFuture<SessionOperationResult> switchSession(String sessionPath);

    /**
     * Reload extensions, skills, prompts, and themes.
     *
     * @return a future that completes when reload is done
     */
    CompletableFuture<Void> reload();

    /**
     * Result of a session operation.
     *
     * @param cancelled whether the operation was cancelled
     */
    record SessionOperationResult(boolean cancelled) { }

    /**
     * Options for creating a new session.
     *
     * @param parentSession parent session path (may be null)
     * @param setup         setup function to run on the session manager (may be null)
     */
    record NewSessionOptions(
        String parentSession,
        Function<SessionManager, CompletableFuture<Void>> setup
    ) { }

    /**
     * Options for tree navigation.
     *
     * @param summarize           whether to generate a summary
     * @param customInstructions  custom instructions for summarization
     * @param replaceInstructions whether to replace default instructions
     * @param label               label for the branch summary entry
     */
    record NavigateTreeOptions(
        Boolean summarize,
        String customInstructions,
        Boolean replaceInstructions,
        String label
    ) { }
}
