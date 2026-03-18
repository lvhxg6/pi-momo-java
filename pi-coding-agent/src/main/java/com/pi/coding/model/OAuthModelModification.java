package com.pi.coding.model;

import java.util.Map;

/**
 * Modification to apply to models when OAuth is configured for a provider.
 * 
 * <p>Allows OAuth providers to override model API endpoints and headers.
 */
public record OAuthModelModification(
    String baseUrl,
    Map<String, String> headers
) {}
