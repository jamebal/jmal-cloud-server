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

    public FileIndex(File file, FileIntroVO fileIntroVO) {
        this.file = file;
        this.userId = fileIntroVO.getUserId();
        this.fileId = fileIntroVO.getId();
        this.path = fileIntroVO.getPath();
        this.isFolder = fileIntroVO.getIsFolder();
        this.isFavorite = fileIntroVO.getIsFavorite();
    }


    private String userId;
    private String username;
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
