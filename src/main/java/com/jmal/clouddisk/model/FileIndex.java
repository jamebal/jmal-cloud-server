package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.file.dto.FileBaseLuceneDTO;
import com.jmal.clouddisk.util.HashUtil;
import com.jmal.clouddisk.util.TimeUntils;
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

    public FileIndex(File file, FileBaseLuceneDTO fileBaseLuceneDTO) {
        this.file = file;
        this.userId = fileBaseLuceneDTO.getUserId();
        this.fileId = fileBaseLuceneDTO.getId();
        this.path = fileBaseLuceneDTO.getPath();
        this.isFolder = fileBaseLuceneDTO.getIsFolder();
        this.isFavorite = fileBaseLuceneDTO.getIsFavorite();
        this.remark = fileBaseLuceneDTO.getRemark();
        this.tagName = getTagName(fileBaseLuceneDTO);
        if (fileBaseLuceneDTO.getTags() != null && !fileBaseLuceneDTO.getTags().isEmpty()) {
            fileBaseLuceneDTO.getTags().forEach(tag -> tagIds.add(tag.getTagId()));
        }
        if (fileBaseLuceneDTO.getTagIds() != null && !fileBaseLuceneDTO.getTagIds().isEmpty()) {
            tagIds.addAll(fileBaseLuceneDTO.getTagIds());
        }
        this.created = TimeUntils.getMilli(fileBaseLuceneDTO.getUploadDate());
    }

    private String getTagName(FileBaseLuceneDTO fileBaseLuceneDTO) {
        if (fileBaseLuceneDTO != null && fileBaseLuceneDTO.getTags() != null && !fileBaseLuceneDTO.getTags().isEmpty()) {
            return fileBaseLuceneDTO.getTags().stream().map(Tag::getName).reduce((a, b) -> a + " " + b).orElse("");
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
    private Long created;
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
                        String.join(",", tagIds) + delimiter +
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
