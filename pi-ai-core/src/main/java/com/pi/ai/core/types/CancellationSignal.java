package com.pi.ai.core.types;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 取消信号，用于中断正在进行的流式请求。
 *
 * <p>基于 {@link AtomicBoolean} 实现，线程安全。
 * 对应 TypeScript 中的 {@code AbortSignal} 概念。
 *
 * <p>用法示例：
 * <pre>{@code
 * CancellationSignal signal = new CancellationSignal();
 * var opts = StreamOptions.builder().signal(signal).build();
 * // ... 在另一个线程中取消
 * signal.cancel();
 * }</pre>
 */
public final class CancellationSignal {

    /** 内部取消状态，保证线程安全。 */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 设置取消状态。多次调用是幂等的。
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * 检查是否已取消。
     *
     * @return 如果 {@link #cancel()} 已被调用则返回 {@code true}
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
