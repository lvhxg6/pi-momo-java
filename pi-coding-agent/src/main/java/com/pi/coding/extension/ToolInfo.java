package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tool information with name, description, and parameter schema.
 *
 * @param name        tool name
 * @param description tool description
 * @param parameters  parameter schema (JSON Schema)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolInfo(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("parameters") JsonNode parameters
) { }
