package com.pi.ai.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.Map;

/**
 * Partial JSON 增量解析器，用于流式传输中解析不完整的 JSON。
 *
 * <p>始终返回有效对象，即使 JSON 不完整也不会抛出异常。
 * 完整 JSON 使用标准 Jackson 解析（最快），不完整 JSON 使用容错解析。
 *
 * <p>对应 TypeScript 中的 {@code utils/json-parse.ts}。
 */
public final class StreamingJsonParser {

    private StreamingJsonParser() {
        // 工具类，禁止实例化
    }

    /**
     * 解析可能不完整的 JSON 字符串。
     *
     * <p>解析策略：
     * <ol>
     *   <li>null/空字符串 → 返回空 Map</li>
     *   <li>标准 Jackson 解析（最快，适用于完整 JSON）</li>
     *   <li>容错解析（尝试补全不完整的 JSON）</li>
     *   <li>全部失败 → 返回空 Map</li>
     * </ol>
     *
     * @param partialJson 可能不完整的 JSON 字符串
     * @return 解析结果，解析失败时返回空 Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseStreamingJson(String partialJson) {
        if (partialJson == null || partialJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        // 尝试标准解析（最快，适用于完整 JSON）
        try {
            Object result = PiAiJson.MAPPER.readValue(partialJson, Object.class);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            return Collections.emptyMap();
        } catch (JsonProcessingException e) {
            // 标准解析失败，尝试容错解析
        }

        // 容错解析：尝试补全不完整的 JSON
        try {
            String repaired = repairPartialJson(partialJson);
            if (repaired != null) {
                Object result = PiAiJson.MAPPER.readValue(repaired, Object.class);
                if (result instanceof Map) {
                    return (Map<String, Object>) result;
                }
            }
        } catch (JsonProcessingException e) {
            // 容错解析也失败
        }

        return Collections.emptyMap();
    }

    /**
     * 尝试修复不完整的 JSON 字符串。
     *
     * <p>简单的状态机：追踪未关闭的括号/花括号/引号，在末尾补全。
     *
     * @param partial 不完整的 JSON
     * @return 修复后的 JSON，无法修复时返回 null
     */
    static String repairPartialJson(String partial) {
        if (partial == null || partial.trim().isEmpty()) {
            return null;
        }

        String trimmed = partial.trim();

        // 必须以 { 或 [ 开头才可能是对象/数组
        if (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[') {
            return null;
        }

        StringBuilder result = new StringBuilder(trimmed);

        // 追踪状态
        boolean inString = false;
        boolean escaped = false;
        // 使用栈追踪未关闭的括号
        StringBuilder bracketStack = new StringBuilder();

        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (ch == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            switch (ch) {
                case '{' -> bracketStack.append('}');
                case '[' -> bracketStack.append(']');
                case '}', ']' -> {
              
                    if (!bracketStack.isEmpty()) {
                        bracketStack.deleteCharAt(bracketStack.length() - 1);
                    }
                }
            }
        }

        // 如果在字符串内部截断，先关闭字符串
        if (inString) {
            result.append('"');
        }

        // 移除末尾的悬挂逗号和冒号
        String current = result.toString().trim();
        while (current.endsWith(",") || current.endsWith(":")) {
            current = current.substring(0, current.length() - 1).trim();
        }
        result = new StringBuilder(current);

        // 补全未关闭的括号（逆序）
        for (int i = bracketStack.length() - 1; i >= 0; i--) {
            result.append(bracketStack.charAt(i));
        }

        return result.toString();
    }
}
