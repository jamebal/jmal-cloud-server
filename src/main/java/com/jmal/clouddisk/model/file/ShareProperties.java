package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.model.OperationPermission;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;

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
        this.operationPermissionList = fileDocument.getOperationPermissionList();
        this.isShare = fileDocument.getIsShare();
    }

    public ShareProperties(Boolean isPrivacy, String extractionCode, Long expiresAt, List<OperationPermission> operationPermissionList, Boolean isShare) {
        this.isPrivacy = isPrivacy;
        this.extractionCode = extractionCode;
        this.expiresAt = expiresAt;
        this.operationPermissionList = operationPermissionList;
        this.isShare = isShare;
    }

    public ShareProperties(Document document) {
        this.isPublic = document.getBoolean("isPublic");
        this.isPrivacy = document.getBoolean("isPrivacy");
        this.extractionCode = document.getString("extractionCode");
        this.expiresAt = document.getLong("expiresAt");
        List<String> operationPermissionList = document.getList("operationPermissionList", String.class);
        if (operationPermissionList != null) {
            this.operationPermissionList = operationPermissionList.stream().map(OperationPermission::fromString).filter(java.util.Objects::nonNull).toList();
        }
        this.isShare = document.getBoolean("isShare");
    }
}
