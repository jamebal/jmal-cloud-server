package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.model.WebsiteSettingDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IUserService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TotpService {

    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;
    private final TextEncryptor textEncryptor;
    private final IUserService userService;
    private final SettingService settingService;

    /**
     * 生成一个新的、唯一的MFA密钥。
     */
    public String generateNewSecret() {
        return secretGenerator.generate();
    }

    /**
     * 为给定的密钥生成二维码图片（作为Base64编码的Data URI）。
     * @param secret MFA密钥
     * @param account 用户的唯一标识，通常是邮箱或用户名
     * @return Base64编码的PNG图片Data URI
     */
    public String generateQrCodeImageUri(String secret, String account) {
        WebsiteSettingDTO websiteSettingDTO = settingService.getWebsiteSetting();
        QrData data = new QrData.Builder()
                .label(account)
                .secret(secret)
                .issuer(CharSequenceUtil.isBlank(websiteSettingDTO.getNetdiskName()) ? "JmalCloud" : websiteSettingDTO.getNetdiskName())
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    /**
     * 验证用户提供的TOTP码是否有效。
     * @param code 用户输入的6位数字码
     * @return 如果验证通过则返回true，否则返回false
     */
    public boolean isCodeInvalid(String code, String username) {
        ConsumerDO consumerDO = userService.getUserInfoByUsername(username);
        if (consumerDO == null) {
            return true;
        }
        if (BooleanUtil.isTrue(consumerDO.getMfaEnabled())) {
            String decryptedSecret = textEncryptor.decrypt(consumerDO.getMfaSecret());
            return !codeVerifier.isValidCode(decryptedSecret, code);
        }
        return true;
    }

    /**
     * 验证用户提供的TOTP码是否有效。
     * @param decryptedSecret 用户的MFA密钥
     * @param code 用户输入的6位数字码
     * @return 如果验证通过则返回true，否则返回false
     */
    public boolean isRawCodeValid(String decryptedSecret, String code) {
        return codeVerifier.isValidCode(decryptedSecret, code);
    }
}
