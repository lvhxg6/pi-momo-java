package com.pi.coding.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.pi.ai.core.registry.ModelRegistry;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.ModelCost;
import com.pi.ai.core.util.PiAiJson;
import com.pi.coding.auth.AuthStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coding agent model registry that extends the built-in model registry.
 * 
 * <p>Supports:
 * <ul>
 *   <li>Built-in models from pi-ai-core's models.json</li>
 *   <li>Custom models from a user-provided models.json file</li>
 *   <li>Dynamic provider registration from extensions</li>
 *   <li>API key resolution via {@link AuthStorage}</li>
 *   <li>OAuth model modifications</li>
 * </ul>
 * 
 * <p>Custom models take precedence over built-in models when there is a conflict.
 */
public class CodingModelRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(CodingModelRegistry.class);
    
    private final AuthStorage authStorage;
    
    /** Built-in models from pi-ai-core: provider -> (modelId -> Model) */
    private final Map<String, Map<String, Model>> builtinModels;
    
    /** Custom models from user's models.json: provider -> (modelId -> Model) */
    private final Map<String, Map<String, Model>> customModels = new ConcurrentHashMap<>();
    
    /** Dynamically registered providers from extensions: providerId -> ProviderConfig */
    private final Map<String, ProviderConfig> dynamicProviders = new ConcurrentHashMap<>();
    
    /** OAuth modifications: provider -> OAuthModelModification */
    private final Map<String, OAuthModelModification> oauthModifications = new ConcurrentHashMap<>();
    
    /**
     * Creates a registry with AuthStorage and optional custom models path.
     * 
     * @param authStorage the auth storage for API key resolution
     * @param modelsJsonPath optional path to custom models.json, may be null
     */
    public CodingModelRegistry(AuthStorage authStorage, String modelsJsonPath) {
        this.authStorage = Objects.requireNonNull(authStorage, "authStorage must not be null");
        this.builtinModels = loadBuiltinModels();
        
        if (modelsJsonPath != null) {
            loadCustomModels(modelsJsonPath);
        }
    }
    
    /**
     * Creates a registry with AuthStorage only (no custom models).
     * 
     * @param authStorage the auth storage for API key resolution
     */
    public CodingModelRegistry(AuthStorage authStorage) {
        this(authStorage, null);
    }
    
    // ========== Model Queries ==========
    
    /**
     * Finds a model by provider and model ID.
     * 
     * <p>Search order: dynamic providers → custom models → built-in models
     * 
     * @param provider the provider ID
     * @param modelId the model ID
     * @return the model, or null if not found
     */
    public Model find(String provider, String modelId) {
        // 1. Check dynamic providers
        ProviderConfig dynProvider = dynamicProviders.get(provider);
        if (dynProvider != null) {
            Model dynModel = buildModelFromProvider(dynProvider, modelId);
            if (dynModel != null) return dynModel;
        }
        
        // 2. Check custom models (take precedence over built-in)
        Map<String, Model> customProviderModels = customModels.get(provider);
        if (customProviderModels != null) {
            Model custom = customProviderModels.get(modelId);
            if (custom != null) return applyOAuthModification(custom);
        }
        
        // 3. Check built-in models
        Model builtin = ModelRegistry.getModel(provider, modelId);
        if (builtin != null) return applyOAuthModification(builtin);
        
        return null;
    }
    
    /**
     * Returns all models that have API keys available.
     * 
     * @return list of available models
     */
    public List<Model> getAvailableModels() {
        List<Model> result = new ArrayList<>();
        
        // Collect all providers
        Set<String> allProviders = new LinkedHashSet<>();
        allProviders.addAll(ModelRegistry.getProviders());
        allProviders.addAll(customModels.keySet());
        allProviders.addAll(dynamicProviders.keySet());
        
        for (String provider : allProviders) {
            if (authStorage.getApiKey(provider) != null) {
                result.addAll(getModelsForProvider(provider));
            }
        }
        
        return Collections.unmodifiableList(result);
    }
    
    /**
     * Returns all registered provider IDs.
     * 
     * @return list of provider IDs
     */
    public List<String> getProviders() {
        Set<String> providers = new LinkedHashSet<>();
        providers.addAll(ModelRegistry.getProviders());
        providers.addAll(customModels.keySet());
        providers.addAll(dynamicProviders.keySet());
        return List.copyOf(providers);
    }
    
    /**
     * Returns all models for a given provider (merged custom + built-in).
     * 
     * @param provider the provider ID
     * @return list of models
     */
    public List<Model> getModelsForProvider(String provider) {
        Map<String, Model> merged = new LinkedHashMap<>();
        
        // Start with built-in
        for (Model m : ModelRegistry.getModels(provider)) {
            merged.put(m.id(), applyOAuthModification(m));
        }
        
        // Override with custom (custom takes precedence)
        Map<String, Model> custom = customModels.get(provider);
        if (custom != null) {
            for (Model m : custom.values()) {
                merged.put(m.id(), applyOAuthModification(m));
            }
        }
        
        // Add dynamic provider models
        ProviderConfig dynProvider = dynamicProviders.get(provider);
        if (dynProvider != null && dynProvider.models() != null) {
            for (ProviderModelConfig mc : dynProvider.models()) {
                Model m = buildModelFromConfig(dynProvider, mc);
                merged.put(m.id(), m);
            }
        }
        
        return List.copyOf(merged.values());
    }
    
    // ========== API Key Resolution ==========
    
    /**
     * Gets the API key for a model.
     * 
     * @param model the model
     * @return a future that completes with the API key
     */
    public CompletableFuture<String> getApiKey(Model model) {
        return getApiKeyForProvider(model.provider());
    }
    
    /**
     * Gets the API key for a provider.
     * 
     * @param provider the provider ID
     * @return a future that completes with the API key
     */
    public CompletableFuture<String> getApiKeyForProvider(String provider) {
        // Check dynamic provider for inline API key
        ProviderConfig dynProvider = dynamicProviders.get(provider);
        if (dynProvider != null && dynProvider.apiKey() != null) {
            return CompletableFuture.completedFuture(dynProvider.apiKey());
        }
        
        // Check if OAuth and needs refresh
        if (authStorage.isUsingOAuth(provider)) {
            return authStorage.refreshIfNeeded(provider);
        }
        
        // Standard API key resolution
        String key = authStorage.getApiKey(provider);
        if (key != null) {
            return CompletableFuture.completedFuture(key);
        }
        
        return CompletableFuture.failedFuture(
            new IllegalStateException("No API key found for provider: " + provider));
    }
    
    /**
     * Checks if a model uses OAuth authentication.
     * 
     * @param model the model
     * @return true if using OAuth
     */
    public boolean isUsingOAuth(Model model) {
        return authStorage.isUsingOAuth(model.provider());
    }
    
    // ========== Dynamic Provider Registration ==========
    
    /**
     * Registers a dynamic provider from an extension.
     * 
     * @param config the provider configuration
     */
    public void registerProvider(ProviderConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(config.id(), "provider id must not be null");
        dynamicProviders.put(config.id(), config);
        log.debug("Registered dynamic provider: {}", config.id());
    }
    
    /**
     * Unregisters a dynamic provider.
     * 
     * @param providerId the provider ID to remove
     */
    public void unregisterProvider(String providerId) {
        dynamicProviders.remove(providerId);
        log.debug("Unregistered dynamic provider: {}", providerId);
    }
    
    /**
     * Applies an OAuth modification to models for a provider.
     * 
     * <p>Used when OAuth is configured to override model API endpoints.
     * 
     * @param provider the provider ID
     * @param modification the modification to apply
     */
    public void applyOAuthModification(String provider, OAuthModelModification modification) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modification, "modification must not be null");
        oauthModifications.put(provider, modification);
        log.debug("Applied OAuth modification for provider: {}", provider);
    }
    
    // ========== Private Helpers ==========
    
    /**
     * Loads built-in models from pi-ai-core's ModelRegistry.
     */
    private Map<String, Map<String, Model>> loadBuiltinModels() {
        Map<String, Map<String, Model>> result = new LinkedHashMap<>();
        for (String provider : ModelRegistry.getProviders()) {
            Map<String, Model> models = new LinkedHashMap<>();
            for (Model m : ModelRegistry.getModels(provider)) {
                models.put(m.id(), m);
            }
            result.put(provider, models);
        }
        return Collections.unmodifiableMap(result);
    }
    
    /**
     * Loads custom models from a user-provided models.json file.
     */
    private void loadCustomModels(String modelsJsonPath) {
        Path path = Path.of(modelsJsonPath);
        if (!Files.exists(path)) {
            log.debug("Custom models.json not found at: {}", modelsJsonPath);
            return;
        }
        
        try {
            String content = Files.readString(path);
            Map<String, Map<String, Model>> loaded = PiAiJson.MAPPER.readValue(
                content,
                new TypeReference<Map<String, Map<String, Model>>>() {}
            );
            
            if (loaded != null) {
                for (Map.Entry<String, Map<String, Model>> entry : loaded.entrySet()) {
                    customModels.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
                }
                log.debug("Loaded custom models from: {}", modelsJsonPath);
            }
        } catch (IOException e) {
            log.warn("Failed to load custom models from {}: {}", modelsJsonPath, e.getMessage());
        }
    }
    
    /**
     * Builds a Model from a dynamic provider config and model ID.
     */
    private Model buildModelFromProvider(ProviderConfig provider, String modelId) {
        if (provider.models() == null) return null;
        
        for (ProviderModelConfig mc : provider.models()) {
            if (modelId.equals(mc.id())) {
                return buildModelFromConfig(provider, mc);
            }
        }
        return null;
    }
    
    /**
     * Builds a Model from provider and model configs.
     */
    private Model buildModelFromConfig(ProviderConfig provider, ProviderModelConfig mc) {
        ModelCost cost = mc.cost() != null ? mc.cost() : new ModelCost(0, 0, 0, 0);
        return new Model(
            mc.id(),
            mc.name() != null ? mc.name() : mc.id(),
            "openai-completions",  // default API type for dynamic providers
            provider.id(),
            provider.baseUrl(),
            mc.reasoning() != null ? mc.reasoning() : false,
            mc.input() != null ? List.of(mc.input().split(",")) : List.of("text"),
            cost,
            mc.contextWindow() != null ? mc.contextWindow() : 128000,
            mc.maxTokens() != null ? mc.maxTokens() : 4096,
            provider.headers(),
            null
        );
    }
    
    /**
     * Applies any OAuth modification to a model.
     */
    private Model applyOAuthModification(Model model) {
        OAuthModelModification mod = oauthModifications.get(model.provider());
        if (mod == null) return model;
        
        // Merge headers
        Map<String, String> mergedHeaders = new LinkedHashMap<>();
        if (model.headers() != null) mergedHeaders.putAll(model.headers());
        if (mod.headers() != null) mergedHeaders.putAll(mod.headers());
        
        return new Model(
            model.id(), model.name(), model.api(), model.provider(),
            mod.baseUrl() != null ? mod.baseUrl() : model.baseUrl(),
            model.reasoning(), model.input(), model.cost(),
            model.contextWindow(), model.maxTokens(),
            mergedHeaders.isEmpty() ? null : mergedHeaders,
            model.compat()
        );
    }
}
