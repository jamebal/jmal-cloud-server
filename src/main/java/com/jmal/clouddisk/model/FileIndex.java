package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.util.HashUtil;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.File;

/**
 * 创建文件索引需要的内容
 */
@Data
@Accessors(chain = true)
public class FileIndex implements Reflective {

    public FileIndex(File file, FileIntroVO fileIntroVO) {
        this.file = file;
        this.userId = fileIntroVO.getUserId();
        this.fileId = fileIntroVO.getId();
        this.path = fileIntroVO.getPath();
        this.isFolder = fileIntroVO.getIsFolder();
        this.isFavorite = fileIntroVO.getIsFavorite();
        this.remark = fileIntroVO.getRemark();
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
    private String remark;

    /**
     * 获取文件ID的哈希值, 用于唯一索引标识, 用于避免重复索引
     * @param fileSha256 文件的SHA-256哈希值
     */
    public String getFileIndexHash(String fileSha256) {
        String delimiter = "|";
        String hashSource =
                safe(fileId) + delimiter +
                        safe(userId) + delimiter +
                        safe(path) + delimiter +
                        safe(name) + delimiter +
                        safe(tagName) + delimiter +
                        isFavorite + delimiter +
                        safe(remark) + delimiter +
                        modified + delimiter +
                        size + delimiter +
                        safe(fileSha256);
        return HashUtil.sha256(hashSource);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
