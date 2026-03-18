package com.pi.coding.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link AuthStorageBackend} for testing.
 * 
 * <p>Credentials are stored in memory and lost when the JVM exits.
 * Thread-safe for concurrent access.
 */
public class InMemoryAuthStorageBackend implements AuthStorageBackend {
    
    private final Map<String, AuthCredential> credentials = new ConcurrentHashMap<>();
    
    @Override
    public Map<String, AuthCredential> load() {
        return new HashMap<>(credentials);
    }
    
    @Override
    public void save(Map<String, AuthCredential> credentials) {
        this.credentials.clear();
        this.credentials.putAll(credentials);
    }
}
