package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.model.OperationPermission;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ShareProperties extends ExtendedProperties {

    private Boolean isPublic;
    private Boolean isPrivacy;
    private String extractionCode;
    private Long expiresAt;
    private List<OperationPermission> operationPermissionList;
    private Boolean isShare;
}
