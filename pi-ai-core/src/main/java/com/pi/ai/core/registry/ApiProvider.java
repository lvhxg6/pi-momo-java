package com.pi.ai.core.registry;

import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.types.Context;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.SimpleStreamOptions;
import com.pi.ai.core.types.StreamOptions;

/**
 * API Provider 接口，每个 LLM 服务提供商需实现此接口。
 *
 * <p>提供 {@link #stream} 和 {@link #streamSimple} 两个方法，
 * 分别对应基础流式调用和带推理参数的简化流式调用。
 *
 * <p>实现类通过 {@link ApiProviderRegistry#register(ApiProvider)} 注册到全局注册表。
 */
public interface ApiProvider {

    /**
     * 返回此 Provider 支持的 API 协议标识。
     *
     * <p>例如 "anthropic-messages"、"openai-completions" 等。
     *
     * @return API 协议标识字符串
     */
    String api();

    /**
     * 发起流式调用，返回事件流。
     *
     * @param model   目标模型定义
     * @param context 调用上下文（系统提示、消息列表、工具列表）
     * @param options 流式调用选项
     * @return 异步事件流，消费者可通过迭代器逐个获取事件
     */
    AssistantMessageEventStream stream(Model model, Context context, StreamOptions options);

    /**
     * 发起带推理参数的简化流式调用，返回事件流。
     *
     * @param model   目标模型定义
     * @param context 调用上下文
     * @param options 简化流式调用选项（含 reasoning 和 thinkingBudgets）
     * @return 异步事件流
     */
    AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options);
}
