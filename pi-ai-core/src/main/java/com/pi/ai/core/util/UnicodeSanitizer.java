package com.pi.ai.core.util;

/**
 * Unicode 代理对清理工具，移除未配对的高/低代理字符。
 *
 * <p>未配对的代理字符（高代理 U+D800-U+DBFF 后面没有低代理 U+DC00-U+DFFF，
 * 或低代理前面没有高代理）会导致 JSON 序列化错误。
 *
 * <p>正确配对的代理对（如 emoji）不受影响。
 *
 * <p>对应 TypeScript 中的 {@code utils/sanitize-unicode.ts}。
 */
public final class UnicodeSanitizer {

    private UnicodeSanitizer() {
        // 工具类，禁止实例化
    }

    /**
     * 移除字符串中未配对的 Unicode 代理字符。
     *
     * <p>Java 中 String 内部使用 UTF-16 编码，代理字符直接以 char 形式存在。
     * 遍历每个 char，检测并移除未配对的高代理和低代理。
     *
     * @param text 输入文本
     * @return 清理后的文本，null 输入返回 null
     */
    public static String sanitizeSurrogates(String text) {
        if (text == null) {
            return null;
        }

        int len = text.length();
        StringBuilder sb = null; // 延迟初始化，大多数字符串不含代理字符

        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);

            if (Character.isHighSurrogate(ch)) {
                // 高代理：检查下一个字符是否为低代理
                if (i + 1 < len && Character.isLowSurrogate(text.charAt(i + 1))) {
                    // 正确配对，保留两个字符
                    if (sb != null) {
                        sb.append(ch);
                        sb.append(text.charAt(i + 1));
                    }
                    i++; // 跳过低代理
                } else {
                    // 未配对的高代理，移除
                    if (sb == null) {
                        sb = new StringBuilder(len);
                        sb.append(text, 0, i);
                    }
                }
            } else if (Character.isLowSurrogate(ch)) {
                // 未配对的低代理（前面没有高代理），移除
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(text, 0, i);
                }
            } else {
                // 普通字符，保留
                if (sb != null) {
                    sb.append(ch);
                }
            }
        }

        return sb != null ? sb.toString() : text;
    }
}
