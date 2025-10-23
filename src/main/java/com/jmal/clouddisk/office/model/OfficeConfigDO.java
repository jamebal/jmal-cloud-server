package com.jmal.clouddisk.office.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.List;

@Getter
@Setter
@Document(collection = "officeConfig")
@Entity
@Table(name = "office_config")
public class OfficeConfigDO extends AuditableTimeEntity implements Reflective {

    private String documentServer;

    private String callbackServer;

    private String secret;

    private Boolean tokenEnabled;

    private List<String> format;

    public OfficeConfigDTO toOfficeConfigCache(TextEncryptor textEncryptor) {
        OfficeConfigDTO officeConfigDTO = new OfficeConfigDTO();
        officeConfigDTO.setDocumentServer(this.documentServer);
        officeConfigDTO.setCallbackServer(this.callbackServer);
        officeConfigDTO.setFormat(this.format);
        officeConfigDTO.setTokenEnabled(this.tokenEnabled);
        if (this.tokenEnabled && this.secret != null) {
            officeConfigDTO.setSecret(textEncryptor.decrypt(this.secret));
        }
        return officeConfigDTO;
    }
}
