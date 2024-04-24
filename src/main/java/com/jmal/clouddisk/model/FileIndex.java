package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.File;

/**
 * 创建文件索引需要的内容
 */
@Data
@Accessors(chain = true)
public class FileIndex {

    /**
     * 创建文件索引
     * @param userId 用户id
     * @param fileId 文件id
     */
    public FileIndex(String userId, String fileId) {
        this.userId = userId;
        this.fileId = fileId;
    }

    public FileIndex(File file, FileDocument fileDocument) {
        this.file = file;
        this.userId = fileDocument.getUserId();
        this.fileId = fileDocument.getId();
        this.path = fileDocument.getPath();
        this.isFolder = fileDocument.getIsFolder();
        this.isFavorite = fileDocument.getIsFavorite();
    }

    /**
     * 创建文件索引
     * @param file 文件
     * @param userId 用户id
     * @param fileId 文件id
     */
    public FileIndex(File file, String userId, String fileId) {
        this.file = file;
        this.userId = userId;
        this.fileId = fileId;
    }

    public String userId;
    private File file;
    private String fileId;
    private String name;
    private String path;
    private String type;
    private String tagName;
    private Long modified;
    private Long size;
    private Boolean isFolder;
    private Boolean isFavorite;

}
