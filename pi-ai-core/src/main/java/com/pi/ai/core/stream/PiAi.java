package com.pi.ai.core.stream;

import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.registry.ApiProvider;
import com.pi.ai.core.registry.ApiProviderRegistry;
import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.Context;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.SimpleStreamOptions;
import com.pi.ai.core.types.StreamOptions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * pi-ai SDK 的统一门面类，提供流式和非流式 LLM 调用入口。
 *
 * <p>所有方法通过 {@link ApiProviderRegistry} 查找对应的 {@link ApiProvider}，
 * 然后委托调用。如果目标 API 没有已注册的 Provider，抛出 {@link IllegalStateException}。
 *
 * <p>用法示例：
 * <pre>{@code
 * // 流式调用
 * AssistantMessageEventStream stream = PiAi.stream(model, context, options);
 * for (AssistantMessageEvent event : stream) {
 *     // 处理事件...
 * }
 *
 * // 非流式调用
 * CompletableFuture<AssistantMessage> future = PiAi.complete(model, context, options);
 * AssistantMessage result = future.join();
 * }</pre>
 */
public final class PiAi {

    /** 初始化回调接口，用于注册内置 Provider */
    @FunctionalInterface
    public interface Initializer {
        void initialize();
    }

    private static volatile Initializer initializer;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private PiAi() {
        // 工具类，禁止实例化
    }

    /**
     * 设置初始化器（通常由 pi-ai-providers 模块提供）。
     * 初始化器在首次调用 stream/complete 时自动执行。
     *
     * @param init 初始化回调
     */
    public static void setInitializer(Initializer init) {
        initializer = init;
        initialized.set(false);
    }

    /**
     * 确保已初始化（注册内置 Provider）。
     */
    private static void ensureInitialized() {
        if (!initialized.get() && initializer != null) {
            if (initialized.compareAndSet(false, true)) {
                initializer.initialize();
            }
        }
    }

    /**
     * 发起流式调用，返回事件流。
     *
     * @param model   目标模型定义
     * @param context 调用上下文
     * @param options 流式调用选项
     * @return 异步事件流
     * @throws IllegalStateException 如果 model.api 没有已注册的 Provider
     */
    public static AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        ensureInitialized();
        ApiProvider provider = resolveProvider(model.api());
        return provider.stream(model, context, options);
    }

    /**
     * 发起非流式调用，返回最终结果的 Future。
     *
     * <p>内部调用 {@link #stream} 并返回其 {@code result()} Future。
     *
     * @param model   目标模型定义
     * @param context 调用上下文
     * @param options 流式调用选项
     * @return 最终 AssistantMessage 的 CompletableFuture
     * @throws IllegalStateException 如果 model.api 没有已注册的 Provider
     */
    public static CompletableFuture<AssistantMessage> complete(Model model, Context context, StreamOptions options) {
        return stream(model, context, options).result();
    }

    /**
     * 发起带推理参数的简化流式调用，返回事件流。
     *
     * @param model   目标模型定义
     * @param context 调用上下文
     * @param options 简化流式调用选项（含 reasoning 和 thinkingBudgets）
     * @return 异步事件流
     * @throws IllegalStateException 如果 model.api 没有已注册的 Provider
     */
    public static AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
        ensureInitialized();
        ApiProvider provider = resolveProvider(model.api());
        return provider.streamSimple(model, context, options);
    }

    /**
     * 发起带推理参数的非流式调用，返回最终结果的 Future。
     *
     * <p>内部调用 {@link #streamSimple} 并返回其 {@code result()} Future。
     *
     * @param model   目标模型定义
     * @param context 调用上下文
     * @param options 简化流式调用选项
     * @return 最终 AssistantMessage 的 CompletableFuture
     * @throws IllegalStateException 如果 model.api 没有已注册的 Provider
     */
    public static CompletableFuture<AssistantMessage> completeSimple(Model model, Context context, SimpleStreamOptions options) {
        return streamSimple(model, context, options).result();
    }

    /**
     * 从注册表中解析 Provider，未注册时抛出异常。
     *
     * @param api API 协议标识
     * @return 已注册的 ApiProvider
     * @throws IllegalStateException 如果没有已注册的 Provider
     */
    private static ApiProvider resolveProvider(String api) {
        ApiProvider provider = ApiProviderRegistry.get(api);
        if (provider == null) {
            throw new IllegalStateException("No API provider registered for api: " + api);
        }
        return provider;
    }
}
