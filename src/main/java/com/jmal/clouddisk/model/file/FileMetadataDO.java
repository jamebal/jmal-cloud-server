package com.jmal.clouddisk.model.file;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.model.Tag;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "files"
        // indexes = {
        //         @Index(name = "idx_name", columnList = "name"),
        //         @Index(name = "idx_size", columnList = "size"),
        //         @Index(name = "idx_update_date", columnList = "updateDate"),
        //         @Index(name = "idx_path_name", columnList = "path, name"),
        //         @Index(name = "idx_user_md5_path", columnList = "userId, path"),
        //         @Index(name = "idx_user_path", columnList = "userId, path"),
        //         @Index(name = "idx_user_path_name", columnList = "userId, path, name"),
        //         @Index(name = "idx_user_is_folder_path", columnList = "userId, isFolder, path"),
        //         @Index(name = "idx_user_is_folder_path_name", columnList = "userId, isFolder, path, name"),
        //         @Index(name = "idx_user_is_folder", columnList = "userId, isFolder"),
        //         @Index(name = "idx_user_is_favorite", columnList = "userId, isFavorite"),
        //         @Index(name = "idx_user_content_type", columnList = "userId, contentType"),
        //         @Index(name = "idx_process_marked_folders", columnList = "needsEtagUpdate, isFolder, lastEtagUpdateRequestAt")
        // }
)
public class FileMetadataDO extends AuditableEntity implements Reflective {

    @Column(length = 24)
    private String userId;
    private Boolean isFolder;
    private String name;
    @Column(length = 64)
    private String md5;
    private String path;
    private Long size;
    @Column(length = 128)
    private String contentType;
    @Column(length = 32)
    private String suffix;
    private Boolean isFavorite;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateDate;
    @Column(length = 24)
    private String mountFileId;
    private String ossFolder;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false, orphanRemoval = true)
    @PrimaryKeyJoinColumn
    private FilePropsDO props;

    private Integer delTag;
    /**
     * 用于存储不同类型的二进制数据，如缩略图、文本内容, 使用文件存储, contentPath就是文件路径 ${rootDir}/${dbDir}/data/${fileId}/content/${fileId}
     */
    private Boolean hasContent;
    /**
     * ${rootDir}/${dbDir}/data/${fileId}/contentText/${fileId}
     */
    private Boolean hasContentText;
    /**
     * ${rootDir}/${dbDir}/data/${fileId}/html/${fileId}
     */
    private Boolean hasHtml;

    private Integer LuceneIndex;

    // =========================== ETag相关字段 ===========================
    @Column(length = 64)
    private String etag;
    private Integer etagUpdateFailedAttempts;
    private Boolean needsEtagUpdate;
    private LocalDateTime lastEtagUpdateRequestAt;
    private String lastEtagUpdateError;

    public FileMetadataDO(FileDocument fileDocument) {
        this.id = fileDocument.getId();
        this.covert(fileDocument);
        this.props = new FilePropsDO(fileDocument);
    }

    public void updateFields(FileDocument fileDocument) {
        this.covert(fileDocument);
        if (this.props == null) {
            this.props = new FilePropsDO(fileDocument);
        } else {
            this.props.updateFields(fileDocument);
        }
    }

    private void covert(FileDocument fileDocument) {
        this.userId = fileDocument.getUserId();
        this.path = fileDocument.getPath();
        this.isFolder = fileDocument.getIsFolder();
        this.name = fileDocument.getName();
        this.size = fileDocument.getSize();
        this.contentType = fileDocument.getContentType();
        this.uploadDate = fileDocument.getUploadDate();
        this.updateDate = fileDocument.getUpdateDate();
        this.suffix = fileDocument.getSuffix();
        this.isFavorite = fileDocument.getIsFavorite();
        this.mountFileId = fileDocument.getMountFileId();
        this.ossFolder = fileDocument.getOssFolder();
        this.etag = fileDocument.getEtag();
        this.etagUpdateFailedAttempts = fileDocument.getEtagUpdateFailedAttempts();
        this.needsEtagUpdate = fileDocument.getNeedsEtagUpdate();
        this.lastEtagUpdateRequestAt = fileDocument.getLastEtagUpdateRequestAt();

        this.delTag = fileDocument.getDelete();
        this.hasContent = fileDocument.getContent() != null;
        this.hasContentText = CharSequenceUtil.isNotBlank(fileDocument.getContentText());
        this.hasHtml = CharSequenceUtil.isNotBlank(fileDocument.getHtml());
        this.LuceneIndex = fileDocument.getIndex();
    }

    public FileDocument toFileDocument() {
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(this.id);
        fileDocument.setIsFolder(this.isFolder);
        fileDocument.setName(this.name);
        fileDocument.setMd5(this.md5);
        fileDocument.setSize(this.size);
        fileDocument.setContentType(this.contentType);
        fileDocument.setUploadDate(this.uploadDate);
        fileDocument.setUpdateDate(this.updateDate);
        fileDocument.setUserId(this.userId);
        fileDocument.setPath(this.path);
        fileDocument.setSuffix(this.suffix);
        fileDocument.setIsFavorite(this.isFavorite);
        if (this.props != null) {
            fileDocument.setShareId(this.props.getShareId());
            fileDocument.setShareBase(this.props.getShareBase());
            fileDocument.setSubShare(this.props.getSubShare());
            List<Tag> tagList = new ArrayList<>(this.props.getTags());
            fileDocument.setTags(tagList);

            this.props.toFileDocumentFragment(fileDocument);
        }
        fileDocument.setDelete(this.getDelTag());
        fileDocument.setIndex(this.getLuceneIndex());
        fileDocument.setEtag(this.etag);
        fileDocument.setEtagUpdateFailedAttempts(this.etagUpdateFailedAttempts);
        fileDocument.setNeedsEtagUpdate(this.needsEtagUpdate);
        fileDocument.setLastEtagUpdateRequestAt(this.lastEtagUpdateRequestAt);
        this.lastEtagUpdateError = fileDocument.getLastEtagUpdateError();
        fileDocument.setMountFileId(this.mountFileId);
        fileDocument.setOssFolder(this.ossFolder);
        return fileDocument;
    }

    public FileMetadataDO(String id, String path, String name, String userId) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.userId = userId;
    }

    @Override
    public int hashCode() {
        int hash = 8;
        hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}
