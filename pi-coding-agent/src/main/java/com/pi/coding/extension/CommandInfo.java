package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Command information for display.
 *
 * @param name        command name (without leading slash)
 * @param description command description
 * @param source      command source ("builtin" or "extension")
 * @param location    source location (extension path for extension commands)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandInfo(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("source") String source,
    @JsonProperty("location") String location
) {

    public static final String SOURCE_BUILTIN = "builtin";
    public static final String SOURCE_EXTENSION = "extension";
}
