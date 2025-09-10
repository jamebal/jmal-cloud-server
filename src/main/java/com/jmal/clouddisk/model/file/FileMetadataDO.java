package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String path;
    private Long size;
    private String contentType;
    private String suffix;
    private Boolean isFavorite;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateDate;
    private String mountFileId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false, orphanRemoval = true)
    @PrimaryKeyJoinColumn
    private FilePropsDO props;

    @OneToOne(mappedBy = "fileMetadata",
            cascade = CascadeType.REMOVE,
            orphanRemoval = true)
    @JsonIgnore
    private ArticleDO article;

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
    }
    public FileMetadataDO covert(FileDocument fileDocument) {
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
        this.etag = fileDocument.getEtag();
        this.etagUpdateFailedAttempts = fileDocument.getEtagUpdateFailedAttempts();
        this.needsEtagUpdate = fileDocument.getNeedsEtagUpdate();
        this.lastEtagUpdateRequestAt = fileDocument.getLastEtagUpdateRequestAt();
        this.lastEtagUpdateError = fileDocument.getLastEtagUpdateError();
        this.props = new FilePropsDO(fileDocument);
        return this;
    }

    public FileDocument toFileDocument() {
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(this.id);
        fileDocument.setIsFolder(this.isFolder);
        fileDocument.setName(this.name);
        fileDocument.setMd5(this.size + this.path + this.name);
        fileDocument.setSize(this.size);
        fileDocument.setContentType(this.contentType);
        fileDocument.setUploadDate(this.uploadDate);
        fileDocument.setUpdateDate(this.updateDate);
        fileDocument.setUserId(this.userId);
        fileDocument.setPath(this.path);
        fileDocument.setSuffix(this.suffix);
        fileDocument.setIsFavorite(this.isFavorite);
        if (this.props != null) {
            fileDocument.setOssPlatform(this.props.getProps().getOssPlatform());
            fileDocument.setOssFolder(this.props.getProps().getOssFolder());
            fileDocument.setIsPublic(this.props.getShareProps().getIsPublic());
            fileDocument.setIsShare(this.props.getShareProps().getIsShare());
            fileDocument.setIsPrivacy(this.props.getShareProps().getIsPrivacy());
            fileDocument.setExtractionCode(this.props.getShareProps().getExtractionCode());
            fileDocument.setShareId(this.props.getShareId());
            fileDocument.setShareBase(this.props.getShareBase());
            fileDocument.setSubShare(this.props.getSubShare());
            fileDocument.setExpiresAt(this.props.getShareProps().getExpiresAt());
            fileDocument.setMusic(this.props.getProps().getMusic());
            fileDocument.setExif(this.props.getProps().getExif());
            fileDocument.setVideo(this.props.getProps().getVideo());
            fileDocument.setW(this.props.getProps().getW());
            fileDocument.setH(this.props.getProps().getH());
            fileDocument.setMediaCover(this.props.getProps().getMediaCover());
            fileDocument.setM3u8(this.props.getProps().getM3u8());
            fileDocument.setVtt(this.props.getProps().getVtt());
            List<Tag> tagList = new ArrayList<>(this.props.getTags());
            fileDocument.setTags(tagList);
            fileDocument.setDelete(this.getProps().getDelTag());
            fileDocument.setShowCover(this.props.getProps().getShowCover());
            fileDocument.setRemark(this.props.getProps().getRemark());
            fileDocument.setIndex(this.props.getLuceneIndex());
            fileDocument.setOperationPermissionList(this.props.getShareProps().getOperationPermissionList());
        }
        fileDocument.setEtag(this.etag);
        fileDocument.setEtagUpdateFailedAttempts(this.etagUpdateFailedAttempts);
        fileDocument.setNeedsEtagUpdate(this.needsEtagUpdate);
        fileDocument.setLastEtagUpdateRequestAt(this.lastEtagUpdateRequestAt);
        fileDocument.setLastEtagUpdateError(this.lastEtagUpdateError);
        fileDocument.setMountFileId(this.mountFileId);
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
