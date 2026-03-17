package com.pi.ai.core.util;

/**
 * 确定性短哈希工具，使用双哈希算法 + Base36 编码。
 *
 * <p>对应 TypeScript 中的 {@code utils/hash.ts}。
 */
public final class ShortHash {

    private ShortHash() {
        // 工具类，禁止实例化
    }

    /**
     * 计算字符串的确定性短哈希值。
     *
     * <p>使用两个不同种子的乘法哈希，输出 Base36 编码字符串。
     * 注意：Java 的 {@code Math.imul} 等价于 {@code (int)(a * b)}（32 位截断乘法）。
     *
     * @param str 输入字符串
     * @return Base36 编码的哈希值
     */
    public static String shortHash(String str) {
        int h1 = 0xdeadbeef;
        int h2 = 0x41c6ce57;

        for (int i = 0; i < str.length(); i++) {
            int ch = str.charAt(i);
            h1 = imul(h1 ^ ch, 0x9E3779B1); // 2654435761
            h2 = imul(h2 ^ ch, 0x5F356495); // 1597334677
        }

        h1 = imul(h1 ^ (h1 >>> 16), 0x85EBCA6B) ^ imul(h2 ^ (h2 >>> 13), 0xC2B2AE35);
        h2 = imul(h2 ^ (h2 >>> 16), 0x85EBCA6B) ^ imul(h1 ^ (h1 >>> 13), 0xC2B2AE35);

        // >>> 0 在 JavaScript 中将有符号 int 转为无符号，Java 中用 Integer.toUnsignedLong
        return Long.toString(Integer.toUnsignedLong(h2), 36)
                + Long.toString(Integer.toUnsignedLong(h1), 36);
    }

    /**
     * 等价于 JavaScript 的 Math.imul：32 位截断乘法。
     * Java 的 int 乘法天然就是 32 位截断的。
     */
    private static int imul(int a, int b) {
        return a * b;
    }
}
