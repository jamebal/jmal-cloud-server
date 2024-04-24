package com.jmal.clouddisk.model;

import lombok.Data;

import java.io.File;

/**
 * 创建文件索引需要的内容
 */
@Data
public class FileIndex {


    public FileIndex(String userId, String fileId) {
        this.userId = userId;
        this.fileId = fileId;
    }

    public FileIndex(String userId, File file, String fileId) {
        this.userId = userId;
        this.file = file;
        this.fileId = fileId;
    }

    public String userId;
    private File file;
    private String fileId;
    private String name;
    private String type;
    private String tagName;
    private Long modified;
    private Long size;
    private Boolean content;
}
