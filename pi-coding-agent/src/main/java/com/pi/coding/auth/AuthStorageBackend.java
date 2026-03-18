package com.pi.coding.auth;

import java.util.Map;

/**
 * Backend interface for credential persistence.
 * 
 * <p>Implementations handle the actual storage mechanism (file, memory, etc.)
 * while {@link AuthStorage} provides the high-level API.
 */
public interface AuthStorageBackend {
    
    /**
     * Loads all stored credentials.
     * 
     * @return map of provider ID to credential, never null
     */
    Map<String, AuthCredential> load();
    
    /**
     * Saves all credentials, replacing any existing data.
     * 
     * @param credentials map of provider ID to credential
     */
    void save(Map<String, AuthCredential> credentials);
}
