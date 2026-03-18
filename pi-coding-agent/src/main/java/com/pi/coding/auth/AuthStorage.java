package com.pi.coding.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication storage for managing provider credentials.
 * 
 * <p>Supports both API key and OAuth credentials with the following resolution order:
 * <ol>
 *   <li>Runtime override (set via {@link #setRuntimeApiKey})</li>
 *   <li>Stored API key credential</li>
 *   <li>OAuth access token (if not expired)</li>
 *   <li>Environment variable ({@code PROVIDER_API_KEY})</li>
 *   <li>Fallback resolver</li>
 * </ol>
 */
public class AuthStorage {
    
    /** Default buffer time before token expiration to trigger refresh (5 minutes). */
    private static final long REFRESH_BUFFER_MS = 5 * 60 * 1000;
    
    private final AuthStorageBackend backend;
    private final Map<String, AuthCredential> credentials;
    private final Map<String, String> runtimeApiKeys = new ConcurrentHashMap<>();
    private final Map<String, OAuthProvider> oauthProviders = new ConcurrentHashMap<>();
    
    private AuthStorage(AuthStorageBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.credentials = new ConcurrentHashMap<>(backend.load());
    }
    
    /**
     * Creates an AuthStorage with file-based persistence.
     * 
     * @param filePath path to the credentials file
     * @return a new AuthStorage instance
     */
    public static AuthStorage create(String filePath) {
        return new AuthStorage(new FileAuthStorageBackend(filePath));
    }
    
    /**
     * Creates an AuthStorage with in-memory storage (for testing).
     * 
     * @return a new AuthStorage instance
     */
    public static AuthStorage inMemory() {
        return new AuthStorage(new InMemoryAuthStorageBackend());
    }
    
    /**
     * Creates an AuthStorage with a custom backend.
     * 
     * @param backend the storage backend
     * @return a new AuthStorage instance
     */
    public static AuthStorage withBackend(AuthStorageBackend backend) {
        return new AuthStorage(backend);
    }
    
    // ========== Credential Management ==========
    
    /**
     * Sets a credential for a provider.
     * 
     * @param provider the provider ID
     * @param credential the credential to store
     */
    public void setCredential(String provider, AuthCredential credential) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(credential, "credential must not be null");
        credentials.put(provider, credential);
        persist();
    }
    
    /**
     * Gets the credential for a provider.
     * 
     * @param provider the provider ID
     * @return the credential, or null if not found
     */
    public AuthCredential getCredential(String provider) {
        return credentials.get(provider);
    }
    
    /**
     * Removes the credential for a provider.
     * 
     * @param provider the provider ID
     */
    public void removeCredential(String provider) {
        credentials.remove(provider);
        persist();
    }
    
    /**
     * Checks if a credential exists for a provider.
     * 
     * @param provider the provider ID
     * @return true if a credential exists
     */
    public boolean hasCredential(String provider) {
        return credentials.containsKey(provider);
    }
    
    // ========== Runtime Override ==========
    
    /**
     * Sets a runtime API key override for a provider.
     * 
     * <p>Runtime overrides take precedence over all other credential sources.
     * 
     * @param provider the provider ID
     * @param apiKey the API key
     */
    public void setRuntimeApiKey(String provider, String apiKey) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(apiKey, "apiKey must not be null");
        runtimeApiKeys.put(provider, apiKey);
    }
    
    /**
     * Clears the runtime API key override for a provider.
     * 
     * @param provider the provider ID
     */
    public void clearRuntimeApiKey(String provider) {
        runtimeApiKeys.remove(provider);
    }
    
    // ========== API Key Resolution ==========
    
    /**
     * Gets the API key for a provider using the standard resolution order.
     * 
     * @param provider the provider ID
     * @return the API key, or null if not found
     */
    public String getApiKey(String provider) {
        return getApiKey(provider, null);
    }
    
    /**
     * Gets the API key for a provider with a fallback resolver.
     * 
     * <p>Resolution order:
     * <ol>
     *   <li>Runtime override</li>
     *   <li>Stored API key credential</li>
     *   <li>OAuth access token (if not expired)</li>
     *   <li>Environment variable ({@code PROVIDER_API_KEY})</li>
     *   <li>Fallback resolver</li>
     * </ol>
     * 
     * @param provider the provider ID
     * @param fallback optional fallback resolver
     * @return the API key, or null if not found
     */
    public String getApiKey(String provider, FallbackResolver fallback) {
        // 1. Runtime override
        String runtimeKey = runtimeApiKeys.get(provider);
        if (runtimeKey != null) {
            return runtimeKey;
        }
        
        // 2. Stored credential
        AuthCredential credential = credentials.get(provider);
        if (credential != null) {
            if (credential instanceof ApiKeyCredential apiKey) {
                return apiKey.apiKey();
            }
            if (credential instanceof OAuthCredential oauth && !oauth.isExpired()) {
                return oauth.accessToken();
            }
        }
        
        // 3. Environment variable
        String envKey = getEnvApiKey(provider);
        if (envKey != null) {
            return envKey;
        }
        
        // 4. Fallback resolver
        if (fallback != null) {
            return fallback.resolve(provider);
        }
        
        return null;
    }
    
    /**
     * Gets the API key from environment variable.
     * 
     * @param provider the provider ID
     * @return the API key from environment, or null
     */
    private String getEnvApiKey(String provider) {
        // Try PROVIDER_API_KEY format (e.g., ANTHROPIC_API_KEY)
        String envName = provider.toUpperCase().replace("-", "_") + "_API_KEY";
        return System.getenv(envName);
    }
    
    // ========== OAuth Flow ==========
    
    /**
     * Registers an OAuth provider for handling OAuth flows.
     * 
     * @param provider the OAuth provider implementation
     */
    public void registerOAuthProvider(OAuthProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        oauthProviders.put(provider.providerId(), provider);
    }
    
    /**
     * Initiates an OAuth login flow.
     * 
     * @param provider the provider ID
     * @param callbacks callbacks for the OAuth flow
     * @return a future that completes when login is done
     */
    public CompletableFuture<Void> login(String provider, OAuthLoginCallbacks callbacks) {
        OAuthProvider oauthProvider = oauthProviders.get(provider);
        if (oauthProvider == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("No OAuth provider registered for: " + provider));
        }
        
        String state = UUID.randomUUID().toString();
        String authUrl = oauthProvider.getAuthorizationUrl(state);
        
        return callbacks.openAuthUrl(authUrl)
            .thenCompose(v -> callbacks.receiveAuthCode())
            .thenCompose(oauthProvider::exchangeCode)
            .thenAccept(credential -> {
                setCredential(provider, credential);
                callbacks.onSuccess(credential);
            })
            .exceptionally(error -> {
                callbacks.onError(error);
                throw new RuntimeException(error);
            });
    }
    
    /**
     * Logs out from a provider by removing OAuth credentials.
     * 
     * @param provider the provider ID
     */
    public void logout(String provider) {
        AuthCredential credential = credentials.get(provider);
        if (credential instanceof OAuthCredential) {
            removeCredential(provider);
        }
    }
    
    /**
     * Refreshes the OAuth token if it's expired or expiring soon.
     * 
     * @param provider the provider ID
     * @return a future that completes with the access token
     */
    public CompletableFuture<String> refreshIfNeeded(String provider) {
        AuthCredential credential = credentials.get(provider);
        
        if (!(credential instanceof OAuthCredential oauth)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No OAuth credential for provider: " + provider));
        }
        
        // Check if refresh is needed
        if (!oauth.isExpiringSoon(REFRESH_BUFFER_MS)) {
            return CompletableFuture.completedFuture(oauth.accessToken());
        }
        
        OAuthProvider oauthProvider = oauthProviders.get(provider);
        if (oauthProvider == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("No OAuth provider registered for: " + provider));
        }
        
        return oauthProvider.refreshToken(oauth)
            .thenApply(refreshed -> {
                setCredential(provider, refreshed);
                return refreshed.accessToken();
            });
    }
    
    /**
     * Checks if the provider is using OAuth authentication.
     * 
     * @param provider the provider ID
     * @return true if using OAuth
     */
    public boolean isUsingOAuth(String provider) {
        return credentials.get(provider) instanceof OAuthCredential;
    }
    
    // ========== Persistence ==========
    
    private void persist() {
        backend.save(new HashMap<>(credentials));
    }
    
    /**
     * Reloads credentials from the backend.
     */
    public void reload() {
        credentials.clear();
        credentials.putAll(backend.load());
    }
}
