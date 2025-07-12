package com.jmal.clouddisk.config;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TotpConfig {

    @Bean
    public SecretGenerator secretGenerator() {
        // 用于生成新的MFA密钥
        return new DefaultSecretGenerator(64);
    }

    @Bean
    public TimeProvider timeProvider() {
        // 提供当前时间，用于TOTP计算
        return new SystemTimeProvider();
    }

    @Bean
    public CodeGenerator codeGenerator() {
        // TOTP代码生成器，使用标准SHA1算法，6位数字，30秒周期
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    }

    @Bean
    public CodeVerifier codeVerifier(TimeProvider timeProvider, CodeGenerator codeGenerator) {
        // 代码验证器
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // 关键：设置容错窗口，允许客户端和服务器之间有轻微的时间偏差
        // 1 表示允许前后一个时间周期（即前后30秒）的代码也有效
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier;
    }

    @Bean
    public QrGenerator qrGenerator() {
        return new ZxingPngQrGenerator();
    }
}
