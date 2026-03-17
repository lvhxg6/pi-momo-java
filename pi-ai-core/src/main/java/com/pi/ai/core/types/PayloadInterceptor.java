package com.pi.ai.core.types;

/**
 * 请求载荷拦截器回调接口。
 *
 * <p>在发送 API 请求前，允许检查或替换 Provider 载荷。
 * 返回 {@code null} 表示保持载荷不变。
 *
 * <p>对应 TypeScript 中的 {@code onPayload} 回调：
 * {@code (payload: unknown, model: Model) => unknown | undefined}
 */
@FunctionalInterface
public interface PayloadInterceptor {

    /**
     * 拦截并可选地替换请求载荷。
     *
     * @param payload 原始请求载荷
     * @param model   目标模型
     * @return 替换后的载荷，或 {@code null} 表示保持不变
     */
    Object intercept(Object payload, Model model);
}
