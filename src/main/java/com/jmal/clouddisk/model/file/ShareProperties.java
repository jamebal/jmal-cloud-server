package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.model.OperationPermission;
import lombok.Data;

import java.util.List;

@Data
public class ShareProperties extends ExtendedProperties {

    private Boolean isPrivacy;
    private String extractionCode;
    private Long expiresAt;
    private List<OperationPermission> operationPermissionList;
    private Boolean subShare;
    private Boolean isShare;
}
