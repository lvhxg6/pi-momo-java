package com.pi.ai.provider.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * SSE（Server-Sent Events）流解析器。
 *
 * <p>从 InputStream 解析 SSE 事件流，返回 {@link Iterator}&lt;{@link SseEvent}&gt;。
 * 支持：
 * <ul>
 *   <li>{@code data:} 字段解析和多行拼接</li>
 *   <li>{@code event:} 字段解析</li>
 *   <li>{@code id:} 字段解析</li>
 *   <li>空行分隔的事件边界</li>
 *   <li>{@code data: [DONE]} 终止标记（OpenAI 风格）</li>
 *   <li>连接中断和解析错误处理</li>
 * </ul>
 */
public final class SseParser {

    private static final Logger log = LoggerFactory.getLogger(SseParser.class);

    /** OpenAI 风格的终止标记 */
    public static final String DONE_MARKER = "[DONE]";

    private SseParser() {
        // 工具类，禁止实例化
    }

    /**
     * SSE 事件记录。
     *
     * @param event 事件类型（event: 字段值，可为 null）
     * @param data  事件数据（data: 字段值，多行拼接）
     * @param id    事件 ID（id: 字段值，可为 null）
     */
    public record SseEvent(String event, String data, String id) {

        /** 判断是否为 [DONE] 终止标记 */
        public boolean isDone() {
            return DONE_MARKER.equals(data);
        }

        /** 判断是否为错误事件（由解析器生成） */
        public boolean isError() {
            return "error".equals(event) && data != null && data.startsWith("SSE parse error:");
        }
    }

    /**
     * 从 InputStream 解析 SSE 事件流。
     *
     * @param inputStream SSE 数据流
     * @return 事件迭代器，逐个返回解析出的 SSE 事件
     */
    public static Iterator<SseEvent> parse(InputStream inputStream) {
        return new SseIterator(inputStream);
    }

    /**
     * SSE 事件迭代器实现。
     */
    private static class SseIterator implements Iterator<SseEvent> {

        private final BufferedReader reader;
        private SseEvent nextEvent;
        private boolean finished;

        SseIterator(InputStream inputStream) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.finished = false;
            this.nextEvent = null;
        }

        @Override
        public boolean hasNext() {
            if (finished) return false;
            if (nextEvent != null) return true;

            try {
                nextEvent = readNextEvent();
                if (nextEvent == null) {
                    finished = true;
                    closeReader();
                    return false;
                }
                return true;
            } catch (IOException e) {
                log.debug("SSE 流读取异常", e);
                // 生成错误事件
                nextEvent = new SseEvent("error", "SSE parse error: " + e.getMessage(), null);
                finished = true;
                closeReader();
                return true;
            }
        }

        @Override
        public SseEvent next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            SseEvent event = nextEvent;
            nextEvent = null;
            return event;
        }

        /**
         * 读取下一个完整的 SSE 事件。
         * 空行分隔事件边界，返回 null 表示流结束。
         */
        private SseEvent readNextEvent() throws IOException {
            StringBuilder dataBuilder = null;
            String eventType = null;
            String eventId = null;
            boolean hasFields = false;

            String line;
            while ((line = reader.readLine()) != null) {
                // 空行 = 事件边界
                if (line.isEmpty()) {
                    if (hasFields && dataBuilder != null) {
                        return new SseEvent(eventType, dataBuilder.toString(), eventId);
                    }
                    // 连续空行或无数据字段，继续读取
                    continue;
                }

                // 忽略注释行
                if (line.startsWith(":")) {
                    continue;
                }

                // 解析字段
                int colonIdx = line.indexOf(':');
                String fieldName;
                String fieldValue;

                if (colonIdx >= 0) {
                    fieldName = line.substring(0, colonIdx);
                    // 冒号后的空格是可选的
                    fieldValue = (colonIdx + 1 < line.length() && line.charAt(colonIdx + 1) == ' ')
                            ? line.substring(colonIdx + 2)
                            : line.substring(colonIdx + 1);
                } else {
                    fieldName = line;
                    fieldValue = "";
                }

                hasFields = true;

                switch (fieldName) {
                    case "data" -> {
                        if (dataBuilder == null) {
                            dataBuilder = new StringBuilder();
                        } else {
                            dataBuilder.append('\n');
                        }
                        dataBuilder.append(fieldValue);
                    }
                    case "event" -> eventType = fieldValue;
                    case "id" -> eventId = fieldValue;
                    // retry 和其他未知字段忽略
                }
            }

            // 流结束，如果有未发送的事件数据则返回
            if (hasFields && dataBuilder != null) {
                return new SseEvent(eventType, dataBuilder.toString(), eventId);
            }

            return null; // 流结束
        }

        private void closeReader() {
            try {
                reader.close();
            } catch (IOException e) {
                log.debug("关闭 SSE reader 异常", e);
            }
        }
    }
}
