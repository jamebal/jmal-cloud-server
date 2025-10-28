package com.jmal.clouddisk.util;

import java.time.LocalDateTime;


public class ShortSignedIdUtilTest {

    public static void main(String[] args) {
        System.out.println("=== Token 生成与验证测试 ===\n");

        String key = "file-123456";
        String pin = "8888";

        // 测试 1: 生成 token
        String token = ShortSignedIdUtil.generateToken(key, pin);
        System.out.println("生成的 Token: " + token);
        System.out.println("Token 长度: " + token.length());
        System.out.println("格式验证: " + ShortSignedIdUtil.isValidTokenFormat(token));

        // 测试 2: 正确验证
        ShortSignedIdUtil.VerificationResult result1 = ShortSignedIdUtil.verifyToken(token, "8888");
        System.out.println("\n✓ 正确 PIN 验证:");
        System.out.println("  Valid: " + result1.isValid());
        System.out.println("  Message: " + result1.getMessage());
        System.out.println("  Key: " + result1.getKey());

        // 测试 3: 错误验证
        ShortSignedIdUtil.VerificationResult result2 = ShortSignedIdUtil.verifyToken(token, "1234");
        System.out.println("\n✗ 错误 PIN 验证:");
        System.out.println("  Valid: " + result2.isValid());
        System.out.println("  Message: " + result2.getMessage());

        // 测试 4: 格式验证
        System.out.println("\n=== 格式验证测试 ===");
        String[] testTokens = {
                "abc123:YWJjZGVm:12345.dGVzdHNpZ25hdHVyZQ",  // ✓ 合法
                "abc-123:YWJjZGVm:12345.dGVzdA",              // ✗ key 包含横线
                "abc123:YWJj:12345.dGVzdA",                   // ✗ pinHash 不足 8 字符
                "abc123:YWJjZGVm:abc.dGVzdA",                 // ✗ 时间戳非数字
                "abc123:YWJjZGVm:12345",                      // ✗ 缺少签名
                token                                         // ✓ 实际生成的 token
        };
        for (String t : testTokens) {
            boolean valid = ShortSignedIdUtil.isValidTokenFormat(t);
            System.out.println("  " + (valid ? "✓" : "✗") + " " + t.substring(0, Math.min(50, t.length())) + (t.length() > 50 ? "..." : ""));
        }

        // 测试 5: 不同 key 的处理
        System.out.println("\n=== Key 处理测试 ===");
        String[] testKeys = {
                "file-123",      // 短 key 带横线
                "file_123",      // 下划线
                "FILE@123!",     // 特殊字符
                "abc123456789",  // 纯字母数字
        };
        for (String k : testKeys) {
            try {
                String t = ShortSignedIdUtil.generateToken(k, "1234");
                System.out.println("  原始: " + k + " -> Token: " + t.substring(0, 20) + "...");
            } catch (Exception e) {
                System.out.println("  原始: " + k + " -> 错误: " + e.getMessage());
            }
        }

        // 测试 6: 过期时间测试
        System.out.println("\n=== 过期时间测试 ===");
        String expiredToken = ShortSignedIdUtil.generateToken(key, pin, LocalDateTime.now().minusHours(1));
        ShortSignedIdUtil.VerificationResult expiredResult = ShortSignedIdUtil.verifyToken(expiredToken, pin);
        System.out.println("  过期 Token 验证: " + expiredResult.isValid() + " - " + expiredResult.getMessage());

        String futureToken = ShortSignedIdUtil.generateToken(key, pin, LocalDateTime.now().plusDays(7));
        ShortSignedIdUtil.VerificationResult futureResult = ShortSignedIdUtil.verifyToken(futureToken, pin);
        System.out.println("  未来 Token 验证: " + futureResult.isValid() + " - " + futureResult.getMessage());
    }

}
