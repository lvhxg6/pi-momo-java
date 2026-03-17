package com.pi.ai.oauth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE（Proof Key for Code Exchange）工具类。
 *
 * <p>生成 code_verifier 和 code_challenge，用于 OAuth 2.0 PKCE 流程。
 * 对应 pi-mono 的 pkce.ts。
 */
public final class PkceUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PkceUtils() {}

    /**
     * 生成 PKCE code_verifier 和 code_challenge。
     *
     * <p>verifier: 32 字节随机数 → Base64URL 编码
     * <p>challenge: SHA-256(verifier) → Base64URL 编码
     *
     * @return PKCE 结果
     */
    public static PkceResult generatePKCE() {
        // Generate random verifier (32 bytes)
        byte[] verifierBytes = new byte[32];
        RANDOM.nextBytes(verifierBytes);
        String verifier = base64UrlEncode(verifierBytes);

        // Compute SHA-256 challenge
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            String challenge = base64UrlEncode(hashBytes);
            return new PkceResult(verifier, challenge);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Base64URL 编码（无填充）。
     */
    static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * PKCE 结果。
     */
    public record PkceResult(String verifier, String challenge) {}
}
