package com.pi.coding.auth;

/**
 * Functional interface for resolving fallback API keys.
 */
@FunctionalInterface
public interface FallbackResolver {
    
    /**
     * Resolves a fallback API key for the given provider.
     * 
     * @param provider the provider ID
     * @return the fallback API key, or null if none available
     */
    String resolve(String provider);
}
