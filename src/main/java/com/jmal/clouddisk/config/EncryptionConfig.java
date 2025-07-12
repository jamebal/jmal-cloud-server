package com.jmal.clouddisk.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    private final EncryptionKeyProvider encryptionKeyProvider;

    @Bean
    public TextEncryptor textEncryptor() {
        String secretKey = encryptionKeyProvider.getSecretKey();
        String salt = encryptionKeyProvider.getSalt();
        return Encryptors.text(secretKey, salt);
    }
}
