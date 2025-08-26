package com.jmal.clouddisk.model.file;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.config.jpa.ExtendedPropertiesConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

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
    private long size;
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

    private Boolean isPublic;
    private Boolean shareBase;
    private String shareId;

    @Column(name = "share_props", columnDefinition = "LONGTEXT")
    @Convert(converter = ExtendedPropertiesConverter.class)
    private ShareProperties shareProps;

    @Column(name = "props", columnDefinition = "LONGTEXT")
    @Convert(converter = ExtendedPropertiesConverter.class)
    private OtherProperties props;

    // =========================== ETag相关字段 ===========================
    private String etag;
    @Column(name = "etag_ufa")
    private Integer etagUpdateFailedAttempts;
    @Column(name = "etag_nu")
    private Boolean needsEtagUpdate;
    @Column(name = "etag_lur")
    private LocalDateTime lastEtagUpdateRequestAt;
    @Column(name = "etag_lue")
    private String lastEtagUpdateError;


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}
