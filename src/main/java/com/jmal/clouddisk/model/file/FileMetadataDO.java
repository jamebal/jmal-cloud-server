package com.jmal.clouddisk.model.file;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditablePerformanceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDateTime;

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
@Table(name = "files",
        indexes = {
                @Index(name = "files_name", columnList = "name"),
                @Index(name = "files_size", columnList = "size"),
                @Index(name = "files_is_folder", columnList = "isFolder"),
                @Index(name = "files_update_date", columnList = "updateDate"),
                @Index(name = "files_upload_date", columnList = "uploadDate"),
                @Index(name = "files_user_id", columnList = "userId"),
                @Index(name = "files_path", columnList = "path"),
                @Index(name = "files_mount_file_id", columnList = "mountFileId"),
                @Index(name = "files_del_tag", columnList = "delTag"),
        }
)
public class FileMetadataDO extends AuditablePerformanceEntity implements Reflective {

    @Column(length = 24, nullable = false)
    private String userId;
    private Boolean isFolder;
    @Column(nullable = false)
    private String name;
    private String md5;
    @Column(nullable = false)
    private String path;
    private Long size;
    private Integer childrenCount;
    @Column(length = 128)
    private String contentType;
    @Column(length = 32)
    private String suffix;
    private Boolean isFavorite;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime uploadDate;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime updateDate;
    private String mountFileId;
    private String ossFolder;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, optional = false, orphanRemoval = true)
    @JoinColumn(name = "props_id", referencedColumnName = "id", unique = true)
    private FilePropsDO props;

    private Integer delTag;
    /**
     * 用于存储不同类型的二进制数据，如缩略图、文本内容, 使用文件存储, contentPath就是文件路径 ${rootDir}/${dbDir}/data/${fileId}/content/${fileId}
     */
    @Column(nullable = false)
    private Boolean hasContent;
    /**
     * ${rootDir}/${dbDir}/data/${fileId}/contentText/${fileId}
     */
    @Column(nullable = false)
    private Boolean hasContentText;
    /**
     * ${rootDir}/${dbDir}/data/${fileId}/html/${fileId}
     */
    @Column(nullable = false)
    private Boolean hasHtml;

    private Integer luceneIndex;

    // =========================== ETag相关字段 ===========================
    @Column(length = 64)
    private String etag;
    private Instant retryAt;
    private Integer etagUpdateFailedAttempts;
    private Boolean needsEtagUpdate;
    private Instant lastEtagUpdateRequestAt;
    @Column(columnDefinition = "TEXT")
    private String lastEtagUpdateError;

    public FileMetadataDO(FileDocument fileDocument) {
        if (fileDocument.getId() == null) {
            fileDocument.setId(new ObjectId().toHexString());
        }
        this.setId(fileDocument.getId());
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
        this.childrenCount = fileDocument.getChildrenCount();
        this.contentType = fileDocument.getContentType();
        this.uploadDate = fileDocument.getUploadDate();
        this.updateDate = fileDocument.getUpdateDate();
        this.suffix = fileDocument.getSuffix();
        this.isFavorite = fileDocument.getIsFavorite();
        this.mountFileId = fileDocument.getMountFileId();
        this.ossFolder = fileDocument.getOssFolder();
        this.etag = fileDocument.getEtag();
        this.retryAt = fileDocument.getRetryAt();
        this.etagUpdateFailedAttempts = fileDocument.getEtagUpdateFailedAttempts();
        this.needsEtagUpdate = fileDocument.getNeedsEtagUpdate();
        this.lastEtagUpdateRequestAt = fileDocument.getLastEtagUpdateRequestAt();

        this.delTag = fileDocument.getDelete();
        this.hasContent = fileDocument.getContent() != null || (fileDocument.getMusic() != null && fileDocument.getMusic().getCoverBase64() != null);
        this.hasContentText = CharSequenceUtil.isNotBlank(fileDocument.getContentText());
        this.hasHtml = CharSequenceUtil.isNotBlank(fileDocument.getHtml());
        this.luceneIndex = fileDocument.getIndex();
    }

    public FileDocument toFileDocument() {
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(this.getId());
        fileDocument.setIsFolder(this.isFolder);
        fileDocument.setName(this.name);
        fileDocument.setMd5(this.md5);
        fileDocument.setSize(this.size);
        fileDocument.setContentType(this.contentType);
        fileDocument.setUploadDate(this.uploadDate);
        fileDocument.setUpdateDate(this.updateDate);
        fileDocument.setUserId(this.userId);
        fileDocument.setPath(this.path);
        fileDocument.setChildrenCount(this.childrenCount);
        fileDocument.setSuffix(this.suffix);
        fileDocument.setIsFavorite(this.isFavorite);
        if (this.props != null) {
            fileDocument.setShareId(this.props.getShareId());
            fileDocument.setRemark(this.props.getRemark());
            fileDocument.setShareBase(this.props.getShareBase());
            fileDocument.setSubShare(this.props.getSubShare());
            fileDocument.setTags(this.props.getTags());
            this.props.toFileDocumentFragment(fileDocument);
        }
        fileDocument.setDelete(this.getDelTag());
        fileDocument.setIndex(this.getLuceneIndex());
        fileDocument.setEtag(this.etag);
        fileDocument.setRetryAt(this.retryAt);
        fileDocument.setEtagUpdateFailedAttempts(this.etagUpdateFailedAttempts);
        fileDocument.setNeedsEtagUpdate(this.needsEtagUpdate);
        fileDocument.setLastEtagUpdateRequestAt(this.lastEtagUpdateRequestAt);
        this.lastEtagUpdateError = fileDocument.getLastEtagUpdateError();
        fileDocument.setMountFileId(this.mountFileId);
        fileDocument.setOssFolder(this.ossFolder);
        return fileDocument;
    }

    public FileIntroVO toFileIntroVO() {
        FileIntroVO fileDocument = new FileIntroVO();
        fileDocument.setId(this.getId());
        fileDocument.setIsFolder(this.isFolder);
        fileDocument.setName(this.name);
        fileDocument.setMd5(this.md5);
        fileDocument.setSize(this.size);
        fileDocument.setChildrenCount(this.childrenCount);
        fileDocument.setContentType(this.contentType);
        fileDocument.setUploadDate(this.uploadDate);
        fileDocument.setUpdateDate(this.updateDate);
        fileDocument.setUserId(this.userId);
        fileDocument.setPath(this.path);
        fileDocument.setSuffix(this.suffix);
        fileDocument.setIsFavorite(this.isFavorite);
        if (this.props != null) {
            fileDocument.setShareBase(this.props.getShareBase());
            fileDocument.setSubShare(this.props.getSubShare());
            fileDocument.setTags(this.props.getTags());
            this.props.toFileIntroVOFragment(fileDocument);
        }
        fileDocument.setEtag(this.etag);
        fileDocument.setMountFileId(this.mountFileId);
        fileDocument.setOssFolder(this.ossFolder);
        return fileDocument;
    }

    public FileMetadataDO(String id, String path, String name, String userId) {
        setId(id);
        this.path = path;
        this.name = name;
        this.userId = userId;
    }

    @Override
    public int hashCode() {
        int hash = 8;
        hash = 89 * hash + (this.getId() != null ? this.getId().hashCode() : 0);
        return hash;
    }

}
