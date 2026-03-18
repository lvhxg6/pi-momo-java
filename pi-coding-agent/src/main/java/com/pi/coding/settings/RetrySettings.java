package com.pi.coding.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Settings for automatic retry behavior.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrySettings(
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("maxRetries") Integer maxRetries,
    @JsonProperty("baseDelayMs") Integer baseDelayMs,
    @JsonProperty("maxDelayMs") Integer maxDelayMs
) {
    public static final RetrySettings DEFAULT = new RetrySettings(true, 3, 1000, 30000);

    public boolean isEnabled() { return enabled != null ? enabled : true; }
    public int getMaxRetries() { return maxRetries != null ? maxRetries : 3; }
    public int getBaseDelayMs() { return baseDelayMs != null ? baseDelayMs : 1000; }
    public int getMaxDelayMs() { return maxDelayMs != null ? maxDelayMs : 30000; }
}
