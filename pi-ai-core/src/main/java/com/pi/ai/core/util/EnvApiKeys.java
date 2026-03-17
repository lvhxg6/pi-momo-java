package com.pi.ai.core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 从环境变量获取 API Key 的工具类。
 *
 * <p>支持 20+ 个 Provider 的环境变量映射，包括特殊的多变量优先级逻辑
 * （如 Anthropic 优先 ANTHROPIC_OAUTH_TOKEN，GitHub Copilot 依次检查三个变量）。
 *
 * <p>对应 TypeScript 中的 {@code env-api-keys.ts}。
 */
public final class EnvApiKeys {

    /** 通用 Provider → 环境变量名映射 */
    private static final Map<String, String> ENV_MAP = Map.ofEntries(
            Map.entry("openai", "OPENAI_API_KEY"),
            Map.entry("azure-openai-responses", "AZURE_OPENAI_API_KEY"),
            Map.entry("google", "GEMINI_API_KEY"),
            Map.entry("groq", "GROQ_API_KEY"),
            Map.entry("cerebras", "CEREBRAS_API_KEY"),
            Map.entry("xai", "XAI_API_KEY"),
            Map.entry("openrouter", "OPENROUTER_API_KEY"),
            Map.entry("vercel-ai-gateway", "AI_GATEWAY_API_KEY"),
            Map.entry("zai", "ZAI_API_KEY"),
            Map.entry("mistral", "MISTRAL_API_KEY"),
            Map.entry("minimax", "MINIMAX_API_KEY"),
            Map.entry("minimax-cn", "MINIMAX_CN_API_KEY"),
            Map.entry("huggingface", "HF_TOKEN"),
            Map.entry("opencode", "OPENCODE_API_KEY"),
            Map.entry("opencode-go", "OPENCODE_API_KEY"),
            Map.entry("kimi-coding", "KIMI_API_KEY")
    );

    /** 缓存 Vertex ADC 凭证检测结果 */
    private static volatile Boolean cachedVertexAdcCredentialsExists;

    private EnvApiKeys() {
        // 工具类，禁止实例化
    }

    /**
     * 根据 provider 名称从环境变量获取 API Key。
     *
     * <p>特殊处理：
     * <ul>
     *   <li>github-copilot: 依次检查 COPILOT_GITHUB_TOKEN、GH_TOKEN、GITHUB_TOKEN</li>
     *   <li>anthropic: 优先 ANTHROPIC_OAUTH_TOKEN，其次 ANTHROPIC_API_KEY</li>
     *   <li>google-vertex: 检查 GOOGLE_CLOUD_API_KEY 或 ADC 凭证</li>
     *   <li>amazon-bedrock: 检查多种 AWS 认证方式</li>
     * </ul>
     *
     * @param provider 服务提供商标识
     * @return API Key 字符串，未找到时返回 null
     */
    public static String getEnvApiKey(String provider) {
        if (provider == null) {
            return null;
        }

        return switch (provider) {
            case "github-copilot" -> firstNonEmpty(
                    getEnv("COPILOT_GITHUB_TOKEN"),
                    getEnv("GH_TOKEN"),
                    getEnv("GITHUB_TOKEN")
            );
            case "anthropic" -> firstNonEmpty(
                    getEnv("ANTHROPIC_OAUTH_TOKEN"),
                    getEnv("ANTHROPIC_API_KEY")
            );
            case "google-vertex" -> resolveVertexApiKey();
            case "amazon-bedrock" -> resolveBedrockApiKey();
            default -> {
                String envVar = ENV_MAP.get(provider);
                yield envVar != null ? getEnv(envVar) : null;
            }
        };
    }

    /**
     * 解析 Google Vertex AI 的 API Key 或 ADC 凭证。
     */
    private static String resolveVertexApiKey() {
        String cloudApiKey = getEnv("GOOGLE_CLOUD_API_KEY");
        if (cloudApiKey != null) {
            return cloudApiKey;
        }

        boolean hasCredentials = hasVertexAdcCredentials();
        boolean hasProject = getEnv("GOOGLE_CLOUD_PROJECT") != null || getEnv("GCLOUD_PROJECT") != null;
        boolean hasLocation = getEnv("GOOGLE_CLOUD_LOCATION") != null;

        if (hasCredentials && hasProject && hasLocation) {
            return "<authenticated>";
        }
        return null;
    }

    /**
     * 解析 Amazon Bedrock 的多种认证方式。
     */
    private static String resolveBedrockApiKey() {
        if (getEnv("AWS_PROFILE") != null
                || (getEnv("AWS_ACCESS_KEY_ID") != null && getEnv("AWS_SECRET_ACCESS_KEY") != null)
                || getEnv("AWS_BEARER_TOKEN_BEDROCK") != null
                || getEnv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") != null
                || getEnv("AWS_CONTAINER_CREDENTIALS_FULL_URI") != null
                || getEnv("AWS_WEB_IDENTITY_TOKEN_FILE") != null) {
            return "<authenticated>";
        }
        return null;
    }

    /**
     * 检测 Google Vertex ADC 凭证文件是否存在。
     */
    private static boolean hasVertexAdcCredentials() {
        if (cachedVertexAdcCredentialsExists == null) {
            // 检查 GOOGLE_APPLICATION_CREDENTIALS 环境变量
            String gacPath = getEnv("GOOGLE_APPLICATION_CREDENTIALS");
            if (gacPath != null) {
                cachedVertexAdcCredentialsExists = Files.exists(Path.of(gacPath));
            } else {
                // 回退到默认 ADC 路径
                String home = System.getProperty("user.home");
                if (home != null) {
                    Path defaultPath = Path.of(home, ".config", "gcloud", "application_default_credentials.json");
                    cachedVertexAdcCredentialsExists = Files.exists(defaultPath);
                } else {
                    cachedVertexAdcCredentialsExists = false;
                }
            }
        }
        return cachedVertexAdcCredentialsExists;
    }

    /**
     * 获取环境变量值，空字符串视为 null。
     */
    static String getEnv(String name) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : null;
    }

    /**
     * 返回第一个非 null 的值。
     */
    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
