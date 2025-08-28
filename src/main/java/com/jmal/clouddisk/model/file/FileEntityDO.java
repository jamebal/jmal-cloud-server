package com.jmal.clouddisk.model.file;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.model.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.format.annotation.DateTimeFormat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Getter
@Setter
@Entity
@Table(name = "files")
public class FileEntityDO extends AuditableEntity implements Reflective {

    public FileEntityDO() {

    }

    private String userId;
    private Boolean isFolder;
    private String name;
    private String md5;
    private Long size;
    private String contentType;
    private String suffix;
    /**
     * 上传时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
    /**
     * 修改时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateDate;
    /**
     * 文件路径(根路径为"/")
     */
    private String path;
    private BlobType blobType;
    @Lob
    private byte[] blob;

    private Boolean isFavorite;
    private String ossFolder;

    private Boolean shareBase;
    private Boolean subShare;
    private String shareId;

    @Column(name = "share_props", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private ShareProperties shareProps;

    @Column(name = "props", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private OtherProperties props;

    @Column(name = "tags", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<Tag> tags = new HashSet<>();

    private String mountFileId;
    private Integer delTag;

    // =========================== ETag相关字段 ===========================
    private String etag;
    private Integer etagUpdateFailedAttempts;
    private Boolean needsEtagUpdate;
    private LocalDateTime lastEtagUpdateRequestAt;
    private String lastEtagUpdateError;

    public FileEntityDO(FileDocument fileDocument) {
        this.id = fileDocument.getId();
        this.userId = fileDocument.getUserId();
        this.path = fileDocument.getPath();
        this.isFolder = fileDocument.getIsFolder();
        this.name = fileDocument.getName();
        this.md5 = fileDocument.getMd5();
        this.size = fileDocument.getSize();
        this.contentType = fileDocument.getContentType();
        this.uploadDate = fileDocument.getUploadDate();
        this.updateDate = fileDocument.getUpdateDate();
        this.suffix = fileDocument.getSuffix();
        this.isFavorite = fileDocument.getIsFavorite();
        this.ossFolder = fileDocument.getOssFolder();
        this.shareBase = fileDocument.getShareBase();
        this.subShare = fileDocument.getSubShare();
        this.shareId = fileDocument.getShareId();

        this.shareProps = new ShareProperties(fileDocument);
        this.props = new OtherProperties(fileDocument);
        this.tags = new HashSet<>();
        if (fileDocument.getTags() != null) {
            this.tags.addAll(fileDocument.getTags());
        }
        this.mountFileId = fileDocument.getMountFileId();
        this.delTag = fileDocument.getDelete();
        this.etag = fileDocument.getEtag();
        this.etagUpdateFailedAttempts = fileDocument.getEtagUpdateFailedAttempts();
        this.needsEtagUpdate = fileDocument.getNeedsEtagUpdate();
        this.lastEtagUpdateRequestAt = fileDocument.getLastEtagUpdateRequestAt();
        this.lastEtagUpdateError = fileDocument.getLastEtagUpdateError();

        if (fileDocument.getContent() != null) {
            this.blobType = BlobType.thumbnail;
            this.blob = fileDocument.getContent();
        }
        if (fileDocument.getContentText() != null) {
            this.blobType = BlobType.contentText;
            this.blob = fileDocument.getContentText().getBytes(StandardCharsets.UTF_8);
        }
        if (fileDocument.getHtml() != null) {
            this.blobType = BlobType.html;
            this.blob = fileDocument.getHtml().getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}
