package com.jmal.clouddisk.model;

import lombok.Data;

import java.io.File;

/**
 * 创建文件索引需要的内容
 */
@Data
public class FileIndex {

    public FileIndex(File file, String fileId) {
        this.file = file;
        this.fileId = fileId;
    }

    public File file;
    private String fileId;
    private Boolean content;
    private String tag;
}
