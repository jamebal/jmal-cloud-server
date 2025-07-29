package com.jmal.clouddisk.config;

import cn.hutool.core.util.HexUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    private final EncryptionKeyProvider encryptionKeyProvider;

    @Bean
    public TextEncryptor textEncryptor() {
        String secretKey = encryptionKeyProvider.getSecretKey();
        String salt = encryptionKeyProvider.getSalt();
        if (!HexUtil.isHexNumber(salt)) {
            salt = HexUtil.encodeHexStr(salt.getBytes(StandardCharsets.UTF_8));
        }
        return Encryptors.text(secretKey, salt);
    }
}
