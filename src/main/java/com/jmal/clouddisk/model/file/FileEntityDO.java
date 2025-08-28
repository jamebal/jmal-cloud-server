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


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}
