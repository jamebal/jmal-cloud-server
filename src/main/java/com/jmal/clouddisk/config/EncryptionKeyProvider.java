package com.jmal.clouddisk.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
@Getter
@Slf4j
public class EncryptionKeyProvider {

    private static final String SECRET_KEY_NAME = "ENCRYPTION_SECRET_KEY";
    private static final String SALT_NAME = "ENCRYPTION_SALT";

    private final FileProperties fileProperties;
    private final Environment environment;

    private String secretKey;
    private String salt;

    @PostConstruct
    public void init() throws IOException {
        // 步骤 1: 优先从环境变量加载
        boolean loadedFromEnv = loadKeysFromEnvironment();

        // 步骤 2: 如果环境变量没有提供密钥，则回退到文件系统
        if (!loadedFromEnv) {
            log.info("加密密钥未在环境变量中找到。正在检查 `.env` 文件...");
            loadKeysFromFileSystem();
        }

        // 步骤 3: 最终验证，确保密钥已成功加载
        if (this.secretKey == null || this.secretKey.isBlank() || this.salt == null || this.salt.isBlank()) {
            throw new IllegalStateException("加载或从任何来源（环境变量、.env文件）生成加密密钥失败。应用程序无法安全启动。");
        }

    }

    /**
     * 尝试从环境变量加载密钥。
     * @return 如果成功从环境变量加载了两个密钥，则返回 true。
     */
    private boolean loadKeysFromEnvironment() {
        String envSecretKey = environment.getProperty(SECRET_KEY_NAME);
        String envSalt = environment.getProperty(SALT_NAME);

        if (envSecretKey != null && !envSecretKey.isBlank() && envSalt != null && !envSalt.isBlank()) {
            log.info("在环境变量中找到 {} 和 {}。使用它们进行加密。", SECRET_KEY_NAME, SALT_NAME);
            this.secretKey = envSecretKey;
            this.salt = envSalt;
            return true;
        }
        return false;
    }

    /**
     * 从 .env 文件加载或创建密钥。
     */
    private void loadKeysFromFileSystem() throws IOException {
        Path envPath = Paths.get(fileProperties.getRootDir(), ".env");

        if (Files.exists(envPath)) {
            log.info("在 [{}] 发现 `.env` 文件, 从该文件中加载加密密钥。", envPath);
            loadKeysFromDotenv(envPath.getParent().toString());
        } else {
            log.warn("在 [{}] 处找不到 `.env` 文件, 生成新的加密密钥并保存到文件。这是一个安全敏感事件。", envPath);
            createNewEnvFileAndSetKeys(envPath);
        }
    }

    private void loadKeysFromDotenv(String directory) {
        Dotenv dotenv = Dotenv.configure().directory(directory).load();
        this.secretKey = dotenv.get(SECRET_KEY_NAME);
        this.salt = dotenv.get(SALT_NAME);
    }

    private void createNewEnvFileAndSetKeys(Path envPath) throws IOException {
        // 生成安全的随机密钥和盐
        String newSecretKey = generateRandomHexString(32);
        String newSalt = generateRandomHexString(16);

        Files.createDirectories(envPath.getParent());

        try (PrintWriter writer = new PrintWriter(envPath.toFile(), StandardCharsets.UTF_8)) {
            writer.println("# 该文件由应用程序在首次运行时自动生成。");
            writer.println("# =======================================================================");
            writer.println("# !! 警告：这是一个极其敏感的安全配置文件，请勿随意修改或删除 !!");
            writer.println("#");
            writer.println("# 更改或丢失此文件中的密钥 (" + SECRET_KEY_NAME + " 或 " + SALT_NAME + ") 将导致：");
            writer.println("#   1. 所有已启用MFA(两步验证)的用户将无法登录，需要重置MFA。");
            writer.println("#   2. 已添加的OSS(对象存储)将无法使用, 需要重新添加。");
            writer.println("#");
            writer.println("# 请务必在安全的位置备份此文件。");
            writer.println("# =======================================================================");
            writer.println();
            writer.println("# 如果您希望通过环境变量来覆盖这些值，请设置名为 " + SECRET_KEY_NAME + " 和 " + SALT_NAME + " 的环境变量。");
            writer.println();
            writer.println(SECRET_KEY_NAME + "=" + newSecretKey);
            writer.println(SALT_NAME + "=" + newSalt);
        }

        log.info("成功在 [{}] 处创建并填充新的 .env 文件。", envPath);

        // 将新生成的密钥加载到当前实例中
        this.secretKey = newSecretKey;
        this.salt = newSalt;
    }

    private String generateRandomHexString(int byteLength) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
