package com.jmal.clouddisk.model;

import lombok.Data;

import java.io.File;

/**
 * 创建文件索引需要的内容
 */
@Data
public class FileIndex {

    public FileIndex(String fileId) {
        this.fileId = fileId;
    }

    public FileIndex(File file, String fileId) {
        this.file = file;
        this.fileId = fileId;
    }

    private File file;
    private String fileId;
    private String name;
    private String type;
    private String tagName;
    private Long modified;
    private Long size;
    private Boolean content;
}
