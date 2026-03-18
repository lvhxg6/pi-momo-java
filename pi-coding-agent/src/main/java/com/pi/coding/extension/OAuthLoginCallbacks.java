package com.pi.coding.extension;

/**
 * Callbacks for OAuth login flow.
 */
public interface OAuthLoginCallbacks {

    /**
     * Open a URL in the user's browser.
     *
     * @param url the URL to open
     */
    void openUrl(String url);

    /**
     * Show a message to the user.
     *
     * @param message the message to show
     */
    void showMessage(String message);

    /**
     * Prompt the user for input.
     *
     * @param prompt the prompt message
     * @return the user's input, or null if cancelled
     */
    String promptInput(String prompt);
}
