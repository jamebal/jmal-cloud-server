package com.jmal.clouddisk.office.model;

import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.service.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.List;

@Data
@Valid
public class OfficeConfigDTO implements Reflective {

    @Pattern(
            regexp = "^(https?://)([\\w.-]+)(:[0-9]+)?(/.*)?$|^$",
            message = "文档服务器地址格式不正确"
    )
    @Schema(name = "documentServer", title = "文档服务器地址", example = "http://localhost:8082/office/word")
    private String documentServer;

    @Schema(name = "secret", title = "密钥")
    private String secret;

    @Pattern(
            regexp = "^(https?://)([\\w.-]+)(:[0-9]+)?(/.*)?$|^$",
            message = "回调服务地址格式不正确"
    )
    @Schema(name = "callbackServer", title = "回调服务地址")
    private String callbackServer;

    @Schema(name = "tokenEnabled", title = "是否启用token", hidden = true)
    public boolean tokenEnabled;

    @Schema(name = "format", title = "默认关联的文件格式")
    private List<String> format;

    public OfficeConfigDO toOfficeConfigDO(TextEncryptor textEncryptor) {
        OfficeConfigDO officeConfigDO = new OfficeConfigDO();
        officeConfigDO.setDocumentServer(this.documentServer);
        officeConfigDO.setCallbackServer(this.callbackServer);
        officeConfigDO.setFormat(this.format);
        boolean tokenEnabled = StrUtil.isNotBlank(this.secret);
        officeConfigDO.setTokenEnabled(tokenEnabled);
        if (Constants.VO_KEY.equals(this.secret)) {
            return officeConfigDO;
        }
        if (tokenEnabled) {
            officeConfigDO.setSecret(textEncryptor.encrypt(this.secret));
        } else {
            officeConfigDO.setSecret(null);
        }
        return officeConfigDO;
    }
}

