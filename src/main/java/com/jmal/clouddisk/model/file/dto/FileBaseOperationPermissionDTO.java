package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.ShareProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@NoArgsConstructor
public class FileBaseOperationPermissionDTO extends FileBaseDTO {

    private List<OperationPermission> operationPermissionList;


    public FileBaseOperationPermissionDTO(String id, String name, String path, String userId, Boolean isFolder, ShareProperties shareProperties) {
        super(id, name, path, userId, isFolder);
        if (shareProperties != null) {
            this.operationPermissionList = shareProperties.getOperationPermissionList();
        }
    }

    public FileDocument toFileDocument() {
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(this.getId());
        fileDocument.setName(this.getName());
        fileDocument.setPath(this.getPath());
        fileDocument.setUserId(this.getUserId());
        fileDocument.setIsFolder(this.getIsFolder());
        fileDocument.setOperationPermissionList(this.getOperationPermissionList());
        return fileDocument;
    }

}
