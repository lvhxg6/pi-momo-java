package com.pi.ai.core.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.Usage;
import com.pi.ai.core.util.PiAiJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型注册表 — 管理所有已注册的 LLM 模型定义。
 *
 * <p>在类加载时从 classpath 的 {@code models.json} 资源文件加载预生成模型定义，
 * 提供按 provider/modelId 查询、费用计算、能力检测等方法。
 */
public final class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    /** 注册表：provider -> (modelId -> Model) */
    private static final Map<String, Map<String, Model>> registry;

    static {
        registry = loadModels();
    }

    private ModelRegistry() {
        // 工具类，禁止实例化
    }

    /**
     * 按 provider 和 modelId 查找模型。
     *
     * @param provider 服务提供商标识（如 "anthropic"、"openai"）
     * @param modelId  模型唯一标识（如 "claude-sonnet-4-20250514"）
     * @return 匹配的 Model，未找到时返回 null
     */
    public static Model getModel(String provider, String modelId) {
        Map<String, Model> providerModels = registry.get(provider);
        if (providerModels == null) {
            return null;
        }
        return providerModels.get(modelId);
    }

    /**
     * 返回所有已注册 Provider 名称列表。
     *
     * @return 不可变的 provider 名称列表
     */
    public static List<String> getProviders() {
        return List.copyOf(registry.keySet());
    }

    /**
     * 返回指定 Provider 下所有模型列表。
     *
     * @param provider 服务提供商标识
     * @return 模型列表，provider 不存在时返回空列表
     */
    public static List<Model> getModels(String provider) {
        Map<String, Model> providerModels = registry.get(provider);
        if (providerModels == null) {
            return Collections.emptyList();
        }
        return List.copyOf(providerModels.values());
    }

    /**
     * 根据模型定价和 token 用量计算费用。
     *
     * <p>公式：{@code (price / 1000000) * tokens}
     *
     * <p>如果模型没有定价信息（cost 为 null），返回零费用。
     *
     * @param model 模型定义（包含定价信息）
     * @param usage token 用量统计
     * @return 费用明细，cost 为 null 时返回全零费用
     */
    public static Usage.Cost calculateCost(Model model, Usage usage) {
        if (model.cost() == null) {
            return new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double input = (model.cost().input() / 1000000.0) * usage.input();
        double output = (model.cost().output() / 1000000.0) * usage.output();
        double cacheRead = (model.cost().cacheRead() / 1000000.0) * usage.cacheRead();
        double cacheWrite = (model.cost().cacheWrite() / 1000000.0) * usage.cacheWrite();
        double total = input + output + cacheRead + cacheWrite;
        return new Usage.Cost(input, output, cacheRead, cacheWrite, total);
    }

    /**
     * 检测模型是否支持 xhigh 思考级别。
     *
     * <p>当前支持：GPT-5.2/5.3/5.4 系列和 Opus 4.6 系列。
     *
     * @param model 模型定义
     * @return 是否支持 xhigh
     */
    public static boolean supportsXhigh(Model model) {
        String id = model.id();
        if (id.contains("gpt-5.2") || id.contains("gpt-5.3") || id.contains("gpt-5.4")) {
            return true;
        }
        if (id.contains("opus-4-6") || id.contains("opus-4.6")) {
            return true;
        }
        return false;
    }

    /**
     * 通过比较 id 和 provider 判断两个模型是否相等。
     *
     * @param a 模型 a（可为 null）
     * @param b 模型 b（可为 null）
     * @return 两个模型是否相等，任一参数为 null 时返回 false
     */
    public static boolean modelsAreEqual(Model a, Model b) {
        if (a == null || b == null) {
            return false;
        }
        return a.id().equals(b.id()) && a.provider().equals(b.provider());
    }

    /**
     * 从 classpath 的 models.json 资源文件加载模型定义。
     */
    private static Map<String, Map<String, Model>> loadModels() {
        try (InputStream is = ModelRegistry.class.getResourceAsStream("/models.json")) {
            if (is == null) {
                log.warn("models.json 资源文件未找到，模型注册表为空");
                return Collections.emptyMap();
            }
            Map<String, Map<String, Model>> raw = PiAiJson.MAPPER.readValue(
                    is,
                    new TypeReference<Map<String, Map<String, Model>>>() { }
            );
            // 使用 LinkedHashMap 保持插入顺序
            Map<String, Map<String, Model>> result = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Model>> entry : raw.entrySet()) {
                result.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
            }
            log.debug("已加载 {} 个 provider 的模型定义", result.size());
            return result;
        } catch (Exception e) {
            log.error("加载 models.json 失败", e);
            return Collections.emptyMap();
        }
    }
}
