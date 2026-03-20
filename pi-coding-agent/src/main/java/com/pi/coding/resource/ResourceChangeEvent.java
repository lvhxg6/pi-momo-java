package com.pi.coding.resource;

import java.util.List;

/**
 * 资源变化事件。
 * 当资源（Skills、Prompts 等）重新加载后，封装重载结果。
 *
 * @param skillsResult  Skills 加载结果
 * @param promptsResult Prompts 加载结果
 * @param diagnostics   诊断信息列表
 * @param timestamp     事件时间戳（毫秒）
 */
public record ResourceChangeEvent(
    LoadSkillsResult skillsResult,
    LoadPromptsResult promptsResult,
    List<ResourceDiagnostic> diagnostics,
    long timestamp
) {
    /**
     * 创建一个只包含 Skills 结果的事件。
     *
     * @param skillsResult Skills 加载结果
     * @return 资源变化事件
     */
    public static ResourceChangeEvent ofSkills(LoadSkillsResult skillsResult) {
        return new ResourceChangeEvent(
            skillsResult,
            null,
            skillsResult != null ? skillsResult.diagnostics() : List.of(),
            System.currentTimeMillis()
        );
    }

    /**
     * 创建一个包含完整结果的事件。
     *
     * @param skillsResult  Skills 加载结果
     * @param promptsResult Prompts 加载结果
     * @param diagnostics   诊断信息
     * @return 资源变化事件
     */
    public static ResourceChangeEvent of(
        LoadSkillsResult skillsResult,
        LoadPromptsResult promptsResult,
        List<ResourceDiagnostic> diagnostics
    ) {
        return new ResourceChangeEvent(
            skillsResult,
            promptsResult,
            diagnostics != null ? diagnostics : List.of(),
            System.currentTimeMillis()
        );
    }
}
