package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.util.HashUtil;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

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
        this.tagName = getTagName(fileIntroVO);
        if (fileIntroVO.getTags() != null) {
            fileIntroVO.getTags().forEach(tag -> tagIds.add(tag.getTagId()));
        }
        if (fileIntroVO.getTagIds() != null) {
            tagIds.addAll(fileIntroVO.getTagIds());
        }
    }

    private String getTagName(FileIntroVO fileIntroVO) {
        if (fileIntroVO != null && fileIntroVO.getTags() != null && !fileIntroVO.getTags().isEmpty()) {
            return fileIntroVO.getTags().stream().map(Tag::getName).reduce((a, b) -> a + " " + b).orElse("");
        }
        return null;
    }


    private String userId;
    private String username;
    private File file;
    private String fileId;
    private String name;
    private String path;
    private String type;
    private String tagName;
    private Set<String> tagIds = new HashSet<>();
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
