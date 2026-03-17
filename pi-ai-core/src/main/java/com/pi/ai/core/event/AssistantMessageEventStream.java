package com.pi.ai.core.event;

import com.pi.ai.core.types.AssistantMessage;

/**
 * 专用于 LLM 响应的 EventStream 特化类。
 *
 * <p>事件类型为 {@link AssistantMessageEvent}，结果类型为 {@link AssistantMessage}。
 * 当 push {@link AssistantMessageEvent.Done} 或 {@link AssistantMessageEvent.Error} 事件时，
 * 自动提取最终 {@link AssistantMessage} 作为 result Future 的值。
 *
 * <p>用法示例：
 * <pre>{@code
 * var stream = AssistantMessageEventStream.create();
 * // 生产者推送事件
 * stream.push(new AssistantMessageEvent.Start(partialMsg));
 * stream.push(new AssistantMessageEvent.TextDelta(0, "hello", partialMsg));
 * stream.push(new AssistantMessageEvent.Done(StopReason.STOP, finalMsg));
 * stream.end(null);
 *
 * // 消费者迭代事件
 * for (AssistantMessageEvent event : stream) {
 *     // 处理事件...
 * }
 * // 获取最终结果
 * AssistantMessage result = stream.result().join();
 * }</pre>
 */
public class AssistantMessageEventStream extends EventStream<AssistantMessageEvent, AssistantMessage> {

    /**
     * 创建 AssistantMessageEventStream 实例。
     *
     * <p>终止条件：事件为 {@link AssistantMessageEvent.Done} 或 {@link AssistantMessageEvent.Error}。
     * 结果提取：Done 事件返回 {@code message}，Error 事件返回 {@code error}。
     */
    public AssistantMessageEventStream() {
        super(
            event -> event instanceof AssistantMessageEvent.Done
                  || event instanceof AssistantMessageEvent.Error,
            event -> {
                if (event instanceof AssistantMessageEvent.Done d) {
                    return d.message();
                }
                if (event instanceof AssistantMessageEvent.Error e) {
                    return e.error();
                }
                throw new IllegalStateException("非预期的终止事件类型: " + event.getClass().getSimpleName());
            }
        );
    }

    /**
     * 工厂方法，创建新的 AssistantMessageEventStream 实例。
     *
     * @return 新的 AssistantMessageEventStream
     */
    public static AssistantMessageEventStream create() {
        return new AssistantMessageEventStream();
    }
}
