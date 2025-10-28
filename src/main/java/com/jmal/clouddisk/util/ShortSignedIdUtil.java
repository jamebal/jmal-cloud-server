package com.jmal.clouddisk.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

@Slf4j
public class ShortSignedIdUtil {

    private final static byte[] keyBytes = SecureUtil.generateKey(SymmetricAlgorithm.AES.getValue(), 256).getEncoded();

    /**
     * shareToken 格式识别正则
     * 格式: {shortKey}:{pinHash}:{relativeExpiry}.{base64Signature}
     * - shortKey: 字母数字组合（无横线的短ID）
     * - pinHash: Base64 URL-safe 编码的 PIN hash（8 字符，无填充）
     * - relativeExpiry: 纯数字（相对时间戳，分钟级）
     * - signature: Base64 URL-safe 编码（无填充，约 43 字符）
     * 示例: abc123:YWJjZGVm:12345.dGVzdHNpZ25hdHVyZWFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6
     */
    private static final Pattern SHARE_TOKEN_PATTERN = Pattern.compile("^[a-zA-Z0-9]+:[A-Za-z0-9_\\-]{8}:[0-9]+\\.[A-Za-z0-9_\\-]+$");

    /**
     * 验证 token 格式是否合法（不验证签名和过期）
     */
    public static boolean isValidTokenFormat(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return SHARE_TOKEN_PATTERN.matcher(token).matches();
    }

    /**
     * 生成 token（包含短密钥）<br>
     * 默认有效期 8 小时
     *
     * @param key 业务 key（如文件 ID）
     * @param pin 短密钥
     */
    public static String generateToken(String key, String pin) {
        return generateToken(key, pin, LocalDateTime.now().plusHours(8));
    }

    /**
     * 生成 token（包含短密钥）<br>
     *
     * @param key           业务 key（如文件 ID）
     * @param pin           短密钥
     * @param expiryTime    过期时间
     */
    public static String generateToken(String key, String pin, LocalDateTime expiryTime) {
        try {
            // 参数校验
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("Key cannot be empty");
            }
            if (pin == null || pin.isEmpty()) {
                throw new IllegalArgumentException("PIN cannot be empty");
            }

            // 压缩 key（移除所有非字母数字字符）
            String shortKey = key.replaceAll("[^a-zA-Z0-9]", "");
            if (shortKey.isEmpty()) {
                throw new IllegalArgumentException("Key must contain alphanumeric characters");
            }

            // 短密钥 hash（固定 8 字符）
            String pinHash = hashPin(pin);

            // 相对时间（分钟级，基于当前年份的 1 月 1 日）
            int currentYear = LocalDateTime.now().getYear();
            long baseTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond();
            long expiry = expiryTime.atZone(ZoneId.systemDefault()).toEpochSecond();

            // 检查过期时间是否在合理范围内（当前年份内）
            if (expiry < baseTime) {
                throw new IllegalArgumentException("Expiry time must be after " + currentYear + "-01-01");
            }

            long relativeMinutes = (expiry - baseTime) / 60;

            // 防止溢出（约 4000 年）
            if (relativeMinutes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Expiry time is too far in the future");
            }

            int relativeExpiry = (int) relativeMinutes;

            // 数据格式: shortKey:pinHash:relativeExpiry
            String data = shortKey + ":" + pinHash + ":" + relativeExpiry;

            byte[] signatureBytes = getSignatureBytes(data);
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            String token = data + "." + signature;

            // 验证生成的 token 格式
            if (!isValidTokenFormat(token)) {
                throw new RuntimeException("Generated token format is invalid: " + token);
            }

            return token;
        } catch (Exception e) {
            log.error("Failed to generate signed ID", e);
            throw new RuntimeException("Failed to generate signed ID", e);
        }
    }

    /**
     * 验证 token（需要提供短密钥）
     *
     * @param token 待验证的 token
     * @param pin   用户输入的短密钥
     */
    public static VerificationResult verifyToken(String token, String pin) {
        try {
            // 0. 格式预检查
            if (!isValidTokenFormat(token)) {
                return new VerificationResult(false, "Invalid token format");
            }

            // 1. 分割 token
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) {
                return new VerificationResult(false, "Invalid token format");
            }
            String data = parts[0];
            String receivedSignature = parts[1];

            // 2. 验证签名
            byte[] signatureBytes = getSignatureBytes(data);
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            if (!expectedSignature.equals(receivedSignature)) {
                return new VerificationResult(false, "Invalid signature");
            }

            // 3. 解析数据
            String[] dataParts = data.split(":", 3);
            if (dataParts.length != 3) {
                return new VerificationResult(false, "Invalid data format");
            }
            String shortKey = dataParts[0];
            String storedPinHash = dataParts[1];
            int relativeExpiry = Integer.parseInt(dataParts[2]);

            // 4. 验证短密钥
            if (pin == null || pin.isEmpty()) {
                return new VerificationResult(false, "PIN is required");
            }
            String inputPinHash = hashPin(pin);
            if (!inputPinHash.equals(storedPinHash)) {
                return new VerificationResult(false, "Invalid PIN");
            }

            // 5. 验证过期时间
            int currentYear = LocalDateTime.now().getYear();
            long baseTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond();
            long expiry = baseTime + ((long) relativeExpiry * 60);
            long currentTime = System.currentTimeMillis() / 1000;

            if (expiry + 5 < currentTime) {
                return new VerificationResult(false, "Token expired");
            }

            return new VerificationResult(true, "Valid", shortKey);

        } catch (NumberFormatException e) {
            return new VerificationResult(false, "Invalid timestamp");
        } catch (Exception e) {
            log.error("Token verification failed", e);
            return new VerificationResult(false, "Verification failed: " + e.getMessage());
        }
    }

    /**
     * 短密钥 hash（固定 8 字符输出）
     */
    private static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            // 只取前 6 字节，Base64 URL-safe 编码后正好 8 个字符（无填充）
            byte[] shortHash = Arrays.copyOf(hash, 6);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(shortHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not found", e);
        }
    }

    private static byte[] getSignatureBytes(String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

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

}
