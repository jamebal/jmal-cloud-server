package com.jmal.clouddisk.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Slf4j
public class ShortSignedIdUtil {

    private final static byte[] keyBytes = SecureUtil.generateKey(SymmetricAlgorithm.AES.getValue(), 256).getEncoded();
    public static String generateToken(String key, LocalDateTime localDateTime) {
        try {
            // 压缩 key（短 ID 优先）
            String shortKey = key.length() > 10 ? key.replace("-", "") : key;
            // 相对时间（分钟级，基于 2025-01-01）
            long baseTime = LocalDateTime.of(2025, 1, 1, 0, 0).atZone(ZoneId.systemDefault()).toEpochSecond();
            long expiry = localDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
            int relativeExpiry = (int) ((expiry - baseTime) / 60);
            String data = shortKey + ":" + relativeExpiry;

            byte[] signatureBytes = getSignatureBytes(data);
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            return data + "." + signature;
        } catch (Exception e) {
            log.error("Failed to generate signed ID", e);
            throw new RuntimeException("Failed to generate signed ID", e);
        }
    }

    private static byte[] getSignatureBytes(String data) throws NoSuchAlgorithmException, InvalidKeyException {
        // HMAC-SHA256 签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    public static VerificationResult verifyToken(String token) {
        try {
            // 分割 token
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return new VerificationResult(false, "Invalid token format");
            }
            String data = parts[0];
            String receivedSignature = parts[1];

            // 验证签名
            byte[] signatureBytes = getSignatureBytes(data);
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            if (!expectedSignature.equals(receivedSignature)) {
                return new VerificationResult(false, "Invalid signature");
            }

            // 解析数据
            String[] dataParts = data.split(":");
            if (dataParts.length != 2) {
                return new VerificationResult(false, "Invalid data format");
            }
            String shortKey = dataParts[0];
            int relativeExpiry = Integer.parseInt(dataParts[1]);

            // 验证过期时间
            long baseTime = LocalDateTime.of(2025, 1, 1, 0, 0).atZone(ZoneId.systemDefault()).toEpochSecond();
            long expiry = baseTime + ((long) relativeExpiry * 60);
            long currentTime = System.currentTimeMillis() / 1000;
            if (expiry + 5 < currentTime) {
                return new VerificationResult(false, "Token expired");
            }

            return new VerificationResult(true, "Valid", shortKey);
        } catch (NumberFormatException e) {
            return new VerificationResult(false, "Invalid timestamp");
        } catch (Exception e) {
            return new VerificationResult(false, "Verification failed: " + e.getMessage());
        }
    }

    // 验证结果类
    @Getter
    public static class VerificationResult {
        private final boolean valid;
        private final String message;
        private final String key;

        public VerificationResult(boolean valid, String message) {
            this(valid, message, null);
        }

        public VerificationResult(boolean valid, String message, String key) {
            this.valid = valid;
            this.message = message;
            this.key = key;
        }

    }

    public static void main(String[] args) {
        String key = "123"; // 短 key，文件 ID
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);

        // 生成 token
        String token = generateToken(key, expiry);
        System.out.println("Token: " + token);
        System.out.println("Length: " + token.length());

        // 验证
        VerificationResult result = verifyToken(token + "1");
        System.out.println("Verified: " + result.isValid());
        System.out.println("Message: " + result.getMessage());
        System.out.println("Key: " + result.getKey());
    }
}
