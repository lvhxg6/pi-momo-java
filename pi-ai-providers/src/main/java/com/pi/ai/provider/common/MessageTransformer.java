package com.pi.ai.provider.common;

import com.pi.ai.core.types.*;

import java.util.*;
import java.util.function.BiFunction;

/**
 * 跨 Provider 消息转换器。
 *
 * <p>将消息列表转换为目标 Model 兼容的格式，处理：
 * <ul>
 *   <li>thinking 块转换（跨模型时转为 TextContent，redacted 丢弃）</li>
 *   <li>thinkingSignature 保留/移除</li>
 *   <li>ToolCall ID 规范化（通过 normalizeToolCallId 回调）</li>
 *   <li>孤立 ToolCall 插入合成错误 ToolResultMessage</li>
 *   <li>过滤 stopReason 为 error/aborted 的 AssistantMessage</li>
 * </ul>
 *
 * <p>对应 TypeScript 中的 {@code transformMessages} 函数。
 */
public final class MessageTransformer {

    private MessageTransformer() {
        // 工具类，禁止实例化
    }

    /**
     * ToolCall ID 规范化回调函数。
     *
     * @param <TApi> API 类型（未使用，保持与 TS 签名一致）
     */
    @FunctionalInterface
    public interface NormalizeToolCallId {
        /**
         * 规范化 ToolCall ID。
         *
         * @param id     原始 ToolCall ID
         * @param model  目标模型
         * @param source 来源 AssistantMessage
         * @return 规范化后的 ID
         */
        String normalize(String id, Model model, AssistantMessage source);
    }

    /**
     * 将消息列表转换为目标 Model 兼容的格式。
     *
     * @param messages           原始消息列表
     * @param model              目标模型
     * @param normalizeToolCallId ToolCall ID 规范化回调（可为 null）
     * @return 转换后的消息列表
     */
    public static List<Message> transformMessages(
            List<Message> messages,
            Model model,
            NormalizeToolCallId normalizeToolCallId) {

        // 原始 ToolCall ID → 规范化 ID 的映射
        Map<String, String> toolCallIdMap = new HashMap<>();

        // ===== 第一遍：转换消息内容（thinking 块、ToolCall ID 规范化） =====
        List<Message> transformed = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                // 用户消息直接透传
                transformed.add(msg);
            } else if (msg instanceof ToolResultMessage trm) {
                // ToolResult 消息：如果有 ID 映射则更新 toolCallId
                String normalizedId = toolCallIdMap.get(trm.toolCallId());
                if (normalizedId != null && !normalizedId.equals(trm.toolCallId())) {
                    transformed.add(new ToolResultMessage(
                            trm.role(), normalizedId, trm.toolName(),
                            trm.content(), trm.details(), trm.isError(), trm.timestamp()));
                } else {
                    transformed.add(trm);
                }
            } else if (msg instanceof AssistantMessage am) {
                boolean isSameModel = Objects.equals(am.getProvider(), model.provider())
                        && Objects.equals(am.getApi(), model.api())
                        && Objects.equals(am.getModel(), model.id());

                List<AssistantContentBlock> transformedContent = new ArrayList<>();
                for (AssistantContentBlock block : am.getContent()) {
                    transformContentBlock(block, isSameModel, model, am,
                            normalizeToolCallId, toolCallIdMap, transformedContent);
                }

                // 构建转换后的 AssistantMessage
                AssistantMessage copy = AssistantMessage.builder()
                        .content(transformedContent)
                        .api(am.getApi())
                        .provider(am.getProvider())
                        .model(am.getModel())
                        .usage(am.getUsage())
                        .stopReason(am.getStopReason())
                        .errorMessage(am.getErrorMessage())
                        .timestamp(am.getTimestamp())
                        .build();
                transformed.add(copy);
            } else {
                transformed.add(msg);
            }
        }

        // ===== 第二遍：插入合成 ToolResult、过滤错误消息 =====
        List<Message> result = new ArrayList<>();
        List<ToolCall> pendingToolCalls = new ArrayList<>();
        Set<String> existingToolResultIds = new HashSet<>();

        for (Message msg : transformed) {
            if (msg instanceof AssistantMessage am) {
                // 如果有上一轮的孤立 ToolCall，先插入合成 ToolResult
                insertSyntheticToolResults(pendingToolCalls, existingToolResultIds, result);
                pendingToolCalls.clear();
                existingToolResultIds.clear();

                // 过滤 error/aborted 的 AssistantMessage
                if (am.getStopReason() == StopReason.ERROR
                        || am.getStopReason() == StopReason.ABORTED) {
                    continue;
                }

                // 收集本轮 ToolCall
                if (am.getContent() != null) {
                    for (AssistantContentBlock block : am.getContent()) {
                        if (block instanceof ToolCall tc) {
                            pendingToolCalls.add(tc);
                        }
                    }
                }

                result.add(am);
            } else if (msg instanceof ToolResultMessage trm) {
                existingToolResultIds.add(trm.toolCallId());
                result.add(trm);
            } else if (msg instanceof UserMessage) {
                // 用户消息中断工具流，插入合成 ToolResult
                insertSyntheticToolResults(pendingToolCalls, existingToolResultIds, result);
                pendingToolCalls.clear();
                existingToolResultIds.clear();
                result.add(msg);
            } else {
                result.add(msg);
            }
        }

        return result;
    }

    /**
     * 转换单个内容块。
     */
    private static void transformContentBlock(
            AssistantContentBlock block,
            boolean isSameModel,
            Model model,
            AssistantMessage source,
            NormalizeToolCallId normalizeToolCallId,
            Map<String, String> toolCallIdMap,
            List<AssistantContentBlock> output) {

        if (block instanceof ThinkingContent tc) {
            // redacted thinking 仅对同模型有效
            if (tc.redacted() != null && tc.redacted()) {
                if (isSameModel) {
                    output.add(tc);
                }
                // 跨模型丢弃 redacted thinking
                return;
            }
            // 同模型且有 thinkingSignature：保留（用于多轮推理连续性）
            if (isSameModel && tc.thinkingSignature() != null) {
                output.add(tc);
                return;
            }
            // 空 thinking 块跳过
            if (tc.thinking() == null || tc.thinking().trim().isEmpty()) {
                return;
            }
            // 同模型保留
            if (isSameModel) {
                output.add(tc);
                return;
            }
            // 跨模型转为 TextContent
            output.add(new TextContent("text", tc.thinking(), null));

        } else if (block instanceof TextContent tc) {
            if (isSameModel) {
                output.add(tc);
            } else {
                // 跨模型移除 textSignature
                output.add(new TextContent("text", tc.text(), null));
            }

        } else if (block instanceof ToolCall tc) {
            ToolCall normalized = tc;

            // 跨模型移除 thoughtSignature
            if (!isSameModel && tc.thoughtSignature() != null) {
                normalized = new ToolCall(tc.type(), tc.id(), tc.name(),
                        tc.arguments(), null);
            }

            // 跨模型且有 normalizeToolCallId 回调：规范化 ID
            if (!isSameModel && normalizeToolCallId != null) {
                String normalizedId = normalizeToolCallId.normalize(tc.id(), model, source);
                if (!normalizedId.equals(tc.id())) {
                    toolCallIdMap.put(tc.id(), normalizedId);
                    normalized = new ToolCall(normalized.type(), normalizedId,
                            normalized.name(), normalized.arguments(),
                            normalized.thoughtSignature());
                }
            }

            output.add(normalized);
        } else {
            output.add(block);
        }
    }

    /**
     * 为孤立的 ToolCall 插入合成错误 ToolResultMessage。
     */
    private static void insertSyntheticToolResults(
            List<ToolCall> pendingToolCalls,
            Set<String> existingToolResultIds,
            List<Message> result) {
        for (ToolCall tc : pendingToolCalls) {
            if (!existingToolResultIds.contains(tc.id())) {
                result.add(new ToolResultMessage(
                        "toolResult",
                        tc.id(),
                        tc.name(),
                        List.of(new TextContent("No result provided")),
                        null,
                        true,
                        System.currentTimeMillis()));
            }
        }
    }
}
