package com.jmal.clouddisk.office.model;

import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.jmal.clouddisk.office.OfficeConfigService;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "officeConfig")
public class OfficeConfigDO {

    private String documentServer;

    private String callbackServer;

    private String encrypted;

    private String key;

    private Boolean tokenEnabled;

    private List<String> format;

    public OfficeConfigDTO toOfficeConfigDTO() {
        OfficeConfigDTO officeConfigDTO = new OfficeConfigDTO();
        officeConfigDTO.setDocumentServer(this.documentServer);
        officeConfigDTO.setCallbackServer(this.callbackServer);
        officeConfigDTO.setFormat(this.format);
        officeConfigDTO.setTokenEnabled(this.tokenEnabled);
        if (this.tokenEnabled) {
            officeConfigDTO.setSecret(OfficeConfigService.VO_KEY);
        }
        return officeConfigDTO;
    }

    public OfficeConfigDTO toOfficeConfigCache() {
        OfficeConfigDTO officeConfigDTO = new OfficeConfigDTO();
        officeConfigDTO.setDocumentServer(this.documentServer);
        officeConfigDTO.setCallbackServer(this.callbackServer);
        officeConfigDTO.setFormat(this.format);
        officeConfigDTO.setTokenEnabled(this.tokenEnabled);
        if (this.tokenEnabled && this.encrypted != null && this.key != null) {
            SymmetricCrypto symmetricCrypto = new SymmetricCrypto(SymmetricAlgorithm.AES, this.key.getBytes());
            String secret = symmetricCrypto.decryptStr(this.encrypted);
            officeConfigDTO.setSecret(secret);
        }
        return officeConfigDTO;
    }
}
