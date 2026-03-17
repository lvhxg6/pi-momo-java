package com.pi.ai.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * JSON Schema 字符串枚举辅助工具。
 *
 * <p>生成兼容 Google API 等不支持 anyOf/const 模式的 Provider 的
 * {@code {"type": "string", "enum": [...]}} 格式 JSON Schema。
 *
 * <p>对应 TypeScript 中的 {@code utils/typebox-helpers.ts} 的 StringEnum 函数。
 */
public final class StringEnumHelper {

    private StringEnumHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 生成字符串枚举 JSON Schema。
     *
     * @param values      枚举值列表
     * @param description 描述（可为 null）
     * @return JSON Schema 节点
     */
    public static JsonNode stringEnum(List<String> values, String description) {
        ObjectNode schema = PiAiJson.MAPPER.createObjectNode();
        schema.put("type", "string");

        ArrayNode enumArray = schema.putArray("enum");
        for (String value : values) {
            enumArray.add(value);
        }

        if (description != null) {
            schema.put("description", description);
        }

        return schema;
    }

    /**
     * 生成字符串枚举 JSON Schema（无描述）。
     *
     * @param values 枚举值列表
     * @return JSON Schema 节点
     */
    public static JsonNode stringEnum(List<String> values) {
        return stringEnum(values, null);
    }
}
