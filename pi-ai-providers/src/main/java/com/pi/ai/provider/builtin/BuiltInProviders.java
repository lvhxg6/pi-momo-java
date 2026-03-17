package com.pi.ai.provider.builtin;

import com.pi.ai.core.registry.ApiProviderRegistry;
import com.pi.ai.provider.anthropic.AnthropicProvider;
import com.pi.ai.provider.bedrock.BedrockProvider;
import com.pi.ai.provider.google.GoogleGeminiCliProvider;
import com.pi.ai.provider.google.GoogleGeminiProvider;
import com.pi.ai.provider.google.GoogleVertexProvider;
import com.pi.ai.provider.mistral.MistralProvider;
import com.pi.ai.provider.openai.AzureOpenAIResponsesProvider;
import com.pi.ai.provider.openai.OpenAICodexResponsesProvider;
import com.pi.ai.provider.openai.OpenAICompletionsProvider;
import com.pi.ai.provider.openai.OpenAIResponsesProvider;

/**
 * 内置 API Provider 注册管理。
 *
 * <p>注册全部 10 个内置 API Provider，对应 pi-mono 的 register-builtins.ts。
 * Bedrock Provider 采用懒加载机制（仅在首次调用时加载 AWS SDK）。
 */
public final class BuiltInProviders {

    private static final String SOURCE_ID = "builtin";

    private BuiltInProviders() {}

    /**
     * 注册全部内置 API Provider。
     */
    public static void registerBuiltInApiProviders() {
        ApiProviderRegistry.register(new AnthropicProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new OpenAICompletionsProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new OpenAIResponsesProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new AzureOpenAIResponsesProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new OpenAICodexResponsesProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new GoogleGeminiProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new GoogleGeminiCliProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new GoogleVertexProvider(), SOURCE_ID);
        ApiProviderRegistry.register(new MistralProvider(), SOURCE_ID);
        // Bedrock Provider 懒加载：注册时不加载 AWS SDK，首次调用时才检查
        ApiProviderRegistry.register(new BedrockProvider(), SOURCE_ID);
    }

    /**
     * 清空所有 Provider 后重新注册内置 Provider。
     */
    public static void resetApiProviders() {
        ApiProviderRegistry.clear();
        registerBuiltInApiProviders();
    }
}
