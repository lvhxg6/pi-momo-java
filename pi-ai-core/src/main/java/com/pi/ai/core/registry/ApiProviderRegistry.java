package com.pi.ai.core.registry;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Provider 全局注册表，管理所有已注册的 {@link ApiProvider} 实现。
 *
 * <p>使用 {@link ConcurrentHashMap} 保证线程安全。注册表为静态单例，
 * 与 TypeScript 版本的模块级变量语义一致。
 *
 * <p>{@code sourceId} 用于标识 Provider 的注册来源，支持按来源批量注销。
 */
public final class ApiProviderRegistry {

    /** 默认 sourceId，用于不指定来源的注册 */
    private static final String DEFAULT_SOURCE_ID = "default";

    /**
     * 已注册的 Provider 包装，携带 sourceId 用于批量注销。
     *
     * @param provider 实际的 ApiProvider 实例
     * @param sourceId 注册来源标识
     */
    private record RegisteredProvider(ApiProvider provider, String sourceId) {}

    /** 按 api 标识存储已注册的 Provider */
    private static final ConcurrentHashMap<String, RegisteredProvider> providers = new ConcurrentHashMap<>();

    private ApiProviderRegistry() {
        // 工具类，禁止实例化
    }

    /**
     * 注册 API Provider，指定来源标识。如果同一 api 已存在，新 Provider 覆盖旧 Provider。
     *
     * @param provider 要注册的 Provider 实例
     * @param sourceId 注册来源标识，用于后续按来源批量注销
     */
    public static void register(ApiProvider provider, String sourceId) {
        providers.put(provider.api(), new RegisteredProvider(provider, sourceId));
    }

    /**
     * 注册 API Provider，使用默认来源标识 "default"。
     * 如果同一 api 已存在，新 Provider 覆盖旧 Provider。
     *
     * <p>此方法保持向后兼容，等价于 {@code register(provider, "default")}。
     *
     * @param provider 要注册的 Provider 实例
     */
    public static void register(ApiProvider provider) {
        register(provider, DEFAULT_SOURCE_ID);
    }

    /**
     * 按 API 协议标识查找已注册的 Provider。
     *
     * @param api API 协议标识
     * @return 对应的 Provider 实例，未注册时返回 null
     */
    public static ApiProvider get(String api) {
        var rp = providers.get(api);
        return rp != null ? rp.provider() : null;
    }

    /**
     * 返回所有已注册的 Provider 列表。
     *
     * @return 不可变的 Provider 列表
     */
    public static List<ApiProvider> getAll() {
        return providers.values().stream()
                .map(RegisteredProvider::provider)
                .toList();
    }

    /**
     * 按来源标识批量注销 Provider。移除所有使用指定 sourceId 注册的 Provider。
     *
     * @param sourceId 要注销的来源标识
     */
    public static void unregister(String sourceId) {
        providers.entrySet().removeIf(e -> e.getValue().sourceId().equals(sourceId));
    }

    /**
     * 清空所有已注册的 Provider。
     */
    public static void clear() {
        providers.clear();
    }
}
