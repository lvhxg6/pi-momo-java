package com.pi.ai.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.pi.ai.core.types.Tool;
import com.pi.ai.core.types.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具参数校验器，使用 JSON Schema 校验 ToolCall 的 arguments。
 *
 * <p>使用 networknt/json-schema-validator 进行校验，支持 coerceTypes。
 *
 * <p>对应 TypeScript 中的 {@code utils/validation.ts}。
 */
public final class ToolValidator {

    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    private ToolValidator() {
        // 工具类，禁止实例化
    }

    /**
     * 按名称查找 Tool 并校验 ToolCall 的 arguments。
     *
     * @param tools    工具定义列表
     * @param toolCall LLM 返回的工具调用
     * @return 校验通过的 arguments（可能经过类型强制转换）
     * @throws IllegalArgumentException 如果工具未找到或校验失败
     */
    public static Map<String, Object> validateToolCall(List<Tool> tools, ToolCall toolCall) {
        Tool tool = tools.stream()
                .filter(t -> t.name().equals(toolCall.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tool \"" + toolCall.name() + "\" not found"));

        return validateToolArguments(tool, toolCall);
    }

    /**
     * 校验 ToolCall 的 arguments 是否符合 Tool 的 JSON Schema。
     *
     * @param tool     工具定义（含 JSON Schema）
     * @param toolCall LLM 返回的工具调用
     * @return 校验通过的 arguments
     * @throws IllegalArgumentException 如果校验失败，消息包含字段路径和错误描述
     */
    public static Map<String, Object> validateToolArguments(Tool tool, ToolCall toolCall) {
        if (tool.parameters() == null) {
            // 无 schema，直接返回 arguments
            return toolCall.arguments();
        }

        // 将 arguments 转为 JsonNode 进行校验
        JsonNode argsNode = PiAiJson.MAPPER.valueToTree(toolCall.arguments());

        // 编译 schema 并校验
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .typeLoose(true) // 等价于 coerceTypes
                .build();
        JsonSchema schema = SCHEMA_FACTORY.getSchema(tool.parameters(), config);
        Set<ValidationMessage> errors = schema.validate(argsNode);

        if (errors.isEmpty()) {
            return toolCall.arguments();
        }

        // 格式化校验错误
        String errorDetails = errors.stream()
                .map(err -> {
                    String path = err.getInstanceLocation().toString();
                    if (path.isEmpty()) {
                        path = "root";
                    }
                    return "  - " + path + ": " + err.getMessage();
                })
                .collect(Collectors.joining("\n"));

        String argsJson;
        try {
            argsJson = PiAiJson.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(toolCall.arguments());
        } catch (Exception e) {
            argsJson = toolCall.arguments().toString();
        }

        throw new IllegalArgumentException(
                "Validation failed for tool \"" + toolCall.name() + "\":\n"
                        + errorDetails + "\n\nReceived arguments:\n" + argsJson);
    }
}
