package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.model.OperationPermission;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShareProperties {

    private Boolean isPublic;
    private Boolean isPrivacy;
    private String extractionCode;
    private Long expiresAt;
    private List<OperationPermission> operationPermissionList;
    private Boolean isShare;

    public ShareProperties(FileDocument fileDocument) {
        this.isPublic = fileDocument.getIsPublic();
        this.isPrivacy = fileDocument.getIsPrivacy();
        this.extractionCode = fileDocument.getExtractionCode();
        this.expiresAt = fileDocument.getExpiresAt();
        this.isShare = fileDocument.getIsShare();
    }
}
