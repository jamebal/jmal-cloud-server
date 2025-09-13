package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.file.FileDocument;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@NoArgsConstructor
public class FileBaseDTO implements Reflective {

    String id;
    String name;
    String path;
    String userId;
    Boolean isFolder;

    public FileBaseDTO(String name, String path, String userId) {
        this.name = name;
        this.path = path;
        this.userId = userId;
    }

    public FileBaseDTO(String id, String name, String path, String userId) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.userId = userId;
    }

    public FileBaseDTO(String id, String name, String path, String userId, Boolean isFolder) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.userId = userId;
        this.isFolder = isFolder;
    }

    public FileBaseDTO(FileDocument fileDocument) {
        this.id = fileDocument.getId();
        this.name = fileDocument.getName();
        this.path = fileDocument.getPath();
        this.userId = fileDocument.getUserId();
        this.isFolder = fileDocument.getIsFolder();
    }


}
