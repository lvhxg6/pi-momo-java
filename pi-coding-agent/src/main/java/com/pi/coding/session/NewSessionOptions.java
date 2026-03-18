package com.pi.coding.session;

/**
 * Options for creating a new session.
 *
 * <p><b>Validates: Requirement 1.2</b>
 *
 * @param id            Optional custom session ID (UUID generated if null)
 * @param parentSession Optional path to parent session file (for forked sessions)
 */
public record NewSessionOptions(
        String id,
        String parentSession
) {
    /**
     * Creates options with default values (auto-generated ID, no parent).
     *
     * @return New options with defaults
     */
    public static NewSessionOptions defaults() {
        return new NewSessionOptions(null, null);
    }

    /**
     * Creates options with a specific parent session.
     *
     * @param parentSession Path to parent session file
     * @return New options with parent session
     */
    public static NewSessionOptions withParent(String parentSession) {
        return new NewSessionOptions(null, parentSession);
    }

    /**
     * Creates options with a specific session ID.
     *
     * @param id Session ID
     * @return New options with custom ID
     */
    public static NewSessionOptions withId(String id) {
        return new NewSessionOptions(id, null);
    }
}
