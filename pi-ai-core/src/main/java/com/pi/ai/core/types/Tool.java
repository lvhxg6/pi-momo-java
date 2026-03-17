package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具定义，包含名称、描述和 JSON Schema 参数描述。
 *
 * <p>{@code parameters} 使用 Jackson {@link JsonNode} 表示 JSON Schema 对象，
 * 与 networknt/json-schema-validator 的输入格式一致。
 *
 * @param name        工具名称
 * @param description 工具描述
 * @param parameters  JSON Schema 参数定义
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Tool(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("parameters") JsonNode parameters
) { }
