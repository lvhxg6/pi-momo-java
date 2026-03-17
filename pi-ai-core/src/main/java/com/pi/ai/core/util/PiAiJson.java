package com.pi.ai.core.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Global Jackson ObjectMapper configuration for pi-ai-java.
 *
 * <p>Configured with:
 * <ul>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES = false} — forward compatibility with new API fields</li>
 *   <li>{@code READ_UNKNOWN_ENUM_VALUES_AS_NULL} — graceful handling of unknown enum values</li>
 *   <li>{@code NON_NULL} serialization inclusion — omit null fields from JSON output</li>
 * </ul>
 */
public final class PiAiJson {

    /**
     * Shared, thread-safe ObjectMapper instance for all pi-ai-java JSON operations.
     */
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private PiAiJson() {
        // Utility class — no instantiation
    }
}
