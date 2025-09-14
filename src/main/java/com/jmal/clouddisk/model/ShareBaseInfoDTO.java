package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 文件分享模型
 * @Author jmal
 * @Date 2020-03-17 16:28
 */
@Data
@NoArgsConstructor
public class ShareBaseInfoDTO implements Reflective {
    private String shareId;
    private String extractionCode;
    private Boolean isPrivacy;
    private LocalDateTime expireDate;
    private List<OperationPermission> operationPermissionList;

    public ShareBaseInfoDTO(String shareId, ShareProperties shareProperties) {
        this.shareId = shareId;
        if (shareProperties != null) {
            this.extractionCode = shareProperties.getExtractionCode();
            this.isPrivacy = shareProperties.getIsPrivacy();
            if (shareProperties.getExpiresAt() != null) {
                this.expireDate = TimeUntils.getLocalDateTime(shareProperties.getExpiresAt());
            }
            this.operationPermissionList = shareProperties.getOperationPermissionList();
        }
    }
}
