package com.pi.agent.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pi.agent.event.ProxyAssistantMessageEvent;
import com.pi.ai.core.event.AssistantMessageEvent;
import com.pi.ai.core.event.AssistantMessageEventStream;
import com.pi.ai.core.types.*;
import com.pi.ai.core.util.PiAiJson;
import com.pi.ai.core.util.StreamingJsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Proxy stream function for apps that route LLM calls through a server.
 * The server manages auth and proxies requests to LLM providers.
 *
 * <p>This is the Java equivalent of the TypeScript {@code streamProxy} function.
 * The server strips the partial field from delta events to reduce bandwidth.
 * We reconstruct the partial message client-side.
 *
 * <p><b>Validates: Requirements 36.1, 36.2, 36.3, 36.4, 36.5, 36.6, 36.7, 36.8, 36.9, 36.10, 36.11, 36.12, 36.13, 36.14, 36.15</b>
 */
public final class ProxyStream {

    /** Shared HttpClient instance for all proxy requests. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private ProxyStream() {
        // Utility class — no instantiation
    }

    /**
     * Stream function that proxies through a server instead of calling LLM providers directly.
     *
     * <p>Use this as the {@code streamFn} option when creating an Agent that needs to go through a proxy.
     *
     * @param model   the LLM model to use
     * @param context the conversation context
     * @param options proxy stream options including authToken and proxyUrl
     * @return an event stream of AssistantMessageEvents
     */
    public static AssistantMessageEventStream streamProxy(
            Model model,
            Context context,
            ProxyStreamOptions options) {

        return streamProxy(model, context, options, null);
    }

    /**
     * Stream function that proxies through a server instead of calling LLM providers directly.
     *
     * <p>Use this as the {@code streamFn} option when creating an Agent that needs to go through a proxy.
     *
     * @param model   the LLM model to use
     * @param context the conversation context
     * @param options proxy stream options including authToken and proxyUrl
     * @param signal  optional cancellation signal to abort the request
     * @return an event stream of AssistantMessageEvents
     */
    public static AssistantMessageEventStream streamProxy(
            Model model,
            Context context,
            ProxyStreamOptions options,
            CancellationSignal signal) {

        var stream = AssistantMessageEventStream.create();

        CompletableFuture.runAsync(() -> {
            // Initialize the partial message that we'll build up from events
            AssistantMessage partial = AssistantMessage.builder()
                    .content(new ArrayList<>())
                    .api(model.api())
                    .provider(model.provider())
                    .model(model.id())
                    .usage(new Usage(0, 0, 0, 0, 0, new Usage.Cost(0, 0, 0, 0, 0)))
                    .stopReason(StopReason.STOP)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Track partial JSON for tool call arguments
            Map<Integer, StringBuilder> partialJsonMap = new HashMap<>();

            HttpResponse<InputStream> response = null;
            try {
                // Build request body
                String requestBody = buildRequestBody(model, context, options);

                // Build HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(options.getProxyUrl() + "/api/stream"))
                        .header("Authorization", "Bearer " + options.getAuthToken())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                // Check cancellation before sending
                if (signal != null && signal.isCancelled()) {
                    throw new CancelledException("Request aborted by user");
                }

                // Send request
                response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

                // Handle non-2xx responses
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorMessage = parseErrorResponse(response);
                    throw new ProxyException(errorMessage);
                }

                // Parse SSE response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Check cancellation in the read loop
                        if (signal != null && signal.isCancelled()) {
                            throw new CancelledException("Request aborted by user");
                        }

                        // Parse SSE data lines
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if (!data.isEmpty()) {
                                ProxyAssistantMessageEvent proxyEvent = PiAiJson.MAPPER.readValue(
                                        data, ProxyAssistantMessageEvent.class);
                                AssistantMessageEvent event = processProxyEvent(
                                        proxyEvent, partial, partialJsonMap);
                                if (event != null) {
                                    stream.push(event);
                                }
                            }
                        }
                    }
                }

                // Check cancellation after reading
                if (signal != null && signal.isCancelled()) {
                    throw new CancelledException("Request aborted by user");
                }

                stream.end();

            } catch (CancelledException e) {
                // Handle cancellation
                partial.setStopReason(StopReason.ABORTED);
                partial.setErrorMessage(e.getMessage());
                stream.push(new AssistantMessageEvent.Error(StopReason.ABORTED, partial));
                stream.end();
            } catch (Exception e) {
                // Handle all other errors
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                partial.setStopReason(StopReason.ERROR);
                partial.setErrorMessage(errorMessage);
                stream.push(new AssistantMessageEvent.Error(StopReason.ERROR, partial));
                stream.end();
            }
        });

        return stream;
    }

    /**
     * Build the JSON request body for the proxy server.
     */
    private static String buildRequestBody(Model model, Context context, ProxyStreamOptions options)
            throws JsonProcessingException {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("context", context);

        Map<String, Object> optionsMap = new HashMap<>();
        if (options.getTemperature() != null) {
            optionsMap.put("temperature", options.getTemperature());
        }
        if (options.getMaxTokens() != null) {
            optionsMap.put("maxTokens", options.getMaxTokens());
        }
        if (options.getReasoning() != null) {
            optionsMap.put("reasoning", options.getReasoning());
        }
        body.put("options", optionsMap);

        return PiAiJson.MAPPER.writeValueAsString(body);
    }

    /**
     * Parse error response body from non-2xx HTTP response.
     */
    private static String parseErrorResponse(HttpResponse<InputStream> response) {
        String baseMessage = "Proxy error: " + response.statusCode();
        try (InputStream body = response.body()) {
            if (body != null) {
                String responseBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                if (!responseBody.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorData = PiAiJson.MAPPER.readValue(responseBody, Map.class);
                    Object error = errorData.get("error");
                    if (error != null) {
                        return "Proxy error: " + error;
                    }
                }
            }
        } catch (Exception e) {
            // Couldn't parse error response, use base message
        }
        return baseMessage;
    }

    /**
     * Process a proxy event and update the partial message.
     *
     * <p>This implements the state machine for reconstructing the partial AssistantMessage
     * from bandwidth-optimized proxy events.
     *
     * @param proxyEvent     the proxy event to process
     * @param partial        the partial message being built
     * @param partialJsonMap map tracking partial JSON for tool call arguments by contentIndex
     * @return the corresponding AssistantMessageEvent, or null if no event should be emitted
     */
    static AssistantMessageEvent processProxyEvent(
            ProxyAssistantMessageEvent proxyEvent,
            AssistantMessage partial,
            Map<Integer, StringBuilder> partialJsonMap) {

        // Use if-else instanceof chains for Java 17 compatibility (no pattern matching in switch)
        if (proxyEvent instanceof ProxyAssistantMessageEvent.Start) {
            return new AssistantMessageEvent.Start(partial);
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.TextStart e) {
            ensureContentSize(partial, e.contentIndex());
            partial.getContent().set(e.contentIndex(), new TextContent("text", "", null));
            return new AssistantMessageEvent.TextStart(e.contentIndex(), partial);
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.TextDelta e) {
            AssistantContentBlock content = partial.getContent().get(e.contentIndex());
            if (content instanceof TextContent tc) {
                // TextContent is a record, so we need to create a new instance
                String newText = tc.text() + e.delta();
                partial.getContent().set(e.contentIndex(), new TextContent("text", newText, tc.textSignature()));
                return new AssistantMessageEvent.TextDelta(e.contentIndex(), e.delta(), partial);
            }
            throw new IllegalStateException("Received text_delta for non-text content");
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.TextEnd e) {
            AssistantContentBlock content = partial.getContent().get(e.contentIndex());
            if (content instanceof TextContent tc) {
                // Set textSignature
                partial.getContent().set(e.contentIndex(),
                        new TextContent("text", tc.text(), e.contentSignature()));
                return new AssistantMessageEvent.TextEnd(e.contentIndex(), tc.text(), partial);
            }
            throw new IllegalStateException("Received text_end for non-text content");
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.ThinkingStart e) {
            ensureContentSize(partial, e.contentIndex());
            partial.getContent().set(e.contentIndex(), new ThinkingContent("thinking", "", null, null));
            return new AssistantMessageEvent.ThinkingStart(e.contentIndex(), partial);
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.ThinkingDelta e) {
            AssistantContentBlock content = partial.getContent().get(e.contentIndex());
            if (content instanceof ThinkingContent tc) {
                String newThinking = tc.thinking() + e.delta();
                partial.getContent().set(e.contentIndex(),
                        new ThinkingContent("thinking", newThinking, tc.thinkingSignature(), tc.redacted()));
                return new AssistantMessageEvent.ThinkingDelta(e.contentIndex(), e.delta(), partial);
            }
            throw new IllegalStateException("Received thinking_delta for non-thinking content");
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.ThinkingEnd e) {
            AssistantContentBlock content = partial.getContent().get(e.contentIndex());
            if (content instanceof ThinkingContent tc) {
                partial.getContent().set(e.contentIndex(),
                        new ThinkingContent("thinking", tc.thinking(), e.contentSignature(), tc.redacted()));
                return new AssistantMessageEvent.ThinkingEnd(e.contentIndex(), tc.thinking(), partial);
            }
            throw new IllegalStateException("Received thinking_end for non-thinking content");
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.ToolCallStart e) {
            ensureContentSize(partial, e.contentIndex());
            partial.getContent().set(e.contentIndex(),
                    new ToolCall("toolCall", e.id(), e.toolName(), new HashMap<>(), null));
            partialJsonMap.put(e.contentIndex(), new StringBuilder());
            return new AssistantMessageEvent.ToolCallStart(e.contentIndex(), partial);
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.ToolCallDelta e) {
            AssistantContentBlock content = partial.getContent().get(e.contentIndex());
            if (content instanceof ToolCall tc) {
                // Append to partial JSON
                StringBuilder partialJson = partialJsonMap.computeIfAbsent(
                        e.contentIndex(), k -> new StringBuilder());
                partialJson.append(e.delta());

                // Parse streaming JSON to get partial arguments
                Map<String, Object> arguments = StreamingJsonParser.parseStreamingJson(partialJson.toString());

                // Update the ToolCall with new arguments
                partial.getContent().set(e.contentIndex(),
                        new ToolCall("toolCall", tc.id(), tc.name(), arguments, tc.thoughtSignature()));

                return new AssistantMessageEvent.ToolCallDelta(e.contentIndex(), e.delta(), partial);
            }
            throw new IllegalStateException("Received toolcall_delta for non-toolCall content");
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.ToolCallEnd e) {
            AssistantContentBlock content = partial.getContent().get(e.contentIndex());
            if (content instanceof ToolCall tc) {
                // Clear partial JSON
                partialJsonMap.remove(e.contentIndex());
                return new AssistantMessageEvent.ToolCallEnd(e.contentIndex(), tc, partial);
            }
            return null;
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.Done e) {
            partial.setStopReason(e.reason());
            partial.setUsage(e.usage());
            return new AssistantMessageEvent.Done(e.reason(), partial);
        }

        if (proxyEvent instanceof ProxyAssistantMessageEvent.Error e) {
            partial.setStopReason(e.reason());
            partial.setErrorMessage(e.errorMessage());
            partial.setUsage(e.usage());
            return new AssistantMessageEvent.Error(e.reason(), partial);
        }

        // Unknown event type - log warning and return null
        System.err.println("Unhandled proxy event type: " + proxyEvent.type());
        return null;
    }

    /**
     * Ensure the content list has enough capacity for the given index.
     */
    private static void ensureContentSize(AssistantMessage partial, int index) {
        while (partial.getContent().size() <= index) {
            partial.getContent().add(null);
        }
    }

    /**
     * Exception thrown when the request is cancelled.
     */
    private static class CancelledException extends Exception {
        CancelledException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown for proxy-specific errors.
     */
    private static class ProxyException extends Exception {
        ProxyException(String message) {
            super(message);
        }
    }
}
