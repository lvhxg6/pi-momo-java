package com.pi.coding.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * API key credential for simple authentication.
 * 
 * @param apiKey the API key value
 */
public record ApiKeyCredential(String apiKey) implements AuthCredential {
    
    @JsonCreator
    public ApiKeyCredential(@JsonProperty("apiKey") String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
    }
    
    @Override
    public String type() {
        return "apiKey";
    }
}
