package com.pi.ai.core.event;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 通用异步事件流基础设施，基于 {@link LinkedBlockingQueue} + {@link CompletableFuture} 实现。
 *
 * <p>生产者通过 {@link #push(Object)} 推送事件，消费者通过 {@link #iterator()} 以阻塞方式逐个获取事件。
 * 流结束时通过 {@link #end(Object)} 标记，或当 push 的事件满足 {@code isComplete} 条件时自动完成。
 *
 * <p>线程安全：push 可以从不同于 iterator 消费的线程调用。
 *
 * @param <E> 事件类型
 * @param <R> 最终结果类型
 */
public class EventStream<E, R> implements Iterable<E>, AutoCloseable {

    /**
     * 哨兵值，用于通知迭代器流已结束。
     * 使用 Object 类型避免泛型擦除问题，在队列中作为特殊标记。
     */
    private static final Object SENTINEL = new Object();

    /** 内部阻塞队列，存储事件和哨兵值 */
    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();

    /** 最终结果的 Future，当 isComplete 事件被 push 或 end() 被调用时完成 */
    private final CompletableFuture<R> finalResult = new CompletableFuture<>();

    /** 判断事件是否为终止事件的谓词 */
    private final Predicate<E> isComplete;

    /** 从终止事件中提取最终结果的函数 */
    private final Function<E, R> extractResult;

    /** 流是否已结束的标志，使用 volatile 保证跨线程可见性 */
    private volatile boolean done = false;

    /**
     * 创建 EventStream 实例。
     *
     * @param isComplete    判断事件是否为终止事件的谓词
     * @param extractResult 从终止事件中提取最终结果的函数
     */
    public EventStream(Predicate<E> isComplete, Function<E, R> extractResult) {
        this.isComplete = isComplete;
        this.extractResult = extractResult;
    }

    /**
     * 生产者推送事件到流中。
     *
     * <p>如果事件满足 {@code isComplete} 条件，则标记流为已完成并设置最终结果。
     * 流结束后再调用 push 是空操作（静默忽略）。
     *
     * @param event 要推送的事件
     */
    public void push(E event) {
        if (done) {
            return;
        }
        if (isComplete.test(event)) {
            done = true;
            finalResult.complete(extractResult.apply(event));
        }
        queue.offer(event);
    }

    /**
     * 标记流结束并设置最终结果。
     *
     * <p>此方法是幂等的，多次调用不会产生副作用。
     * 调用后会向队列放入哨兵值，通知所有等待的迭代器流已结束。
     *
     * @param result 最终结果，可以为 null
     */
    public void end(R result) {
        done = true;
        if (result != null) {
            // 使用 complete 而非 completeExceptionally，如果已完成则忽略
            finalResult.complete(result);
        }
        queue.offer(SENTINEL);
    }

    /**
     * 无结果地标记流结束。
     *
     * <p>仅通知迭代器流已结束，不设置最终结果。
     */
    public void end() {
        end(null);
    }

    /**
     * 返回阻塞式迭代器，按推送顺序逐个获取事件。
     *
     * <p>迭代器的 {@code hasNext()} 方法会阻塞等待直到有事件可用或流结束。
     * 当收到哨兵值时，{@code hasNext()} 返回 false。
     *
     * @return 阻塞式事件迭代器
     */
    @Override
    public Iterator<E> iterator() {
        return new Iterator<>() {
            /** 预取的下一个事件 */
            private E next = null;
            /** 迭代器是否已结束 */
            private boolean finished = false;

            @Override
            public boolean hasNext() {
                if (finished) {
                    return false;
                }
                try {
                    // 阻塞等待下一个元素
                    Object taken = queue.take();
                    if (taken == SENTINEL) {
                        finished = true;
                        // 将哨兵值放回队列，以便其他迭代器也能收到结束信号
                        queue.offer(SENTINEL);
                        return false;
                    }
                    @SuppressWarnings("unchecked")
                    E event = (E) taken;
                    next = event;
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    finished = true;
                    return false;
                }
            }

            @Override
            public E next() {
                if (finished || next == null) {
                    throw new NoSuchElementException("No more events in stream");
                }
                E result = next;
                next = null;
                return result;
            }
        };
    }

    /**
     * 获取最终结果的 Future。
     *
     * <p>当 isComplete 事件被 push 或 end(result) 被调用时，此 Future 完成。
     *
     * @return 最终结果的 CompletableFuture
     */
    public CompletableFuture<R> result() {
        return finalResult;
    }

    /**
     * 关闭流。如果流尚未结束，调用 {@link #end()} 标记结束。
     *
     * <p>支持 try-with-resources 模式。
     */
    @Override
    public void close() {
        if (!done) {
            end();
        }
    }
}
