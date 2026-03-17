package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cache retention policy for prompt caching.
 */
public enum CacheRetention {

    @JsonProperty("none")
    NONE,

    @JsonProperty("short")
    SHORT,

    @JsonProperty("long")
    LONG
}
