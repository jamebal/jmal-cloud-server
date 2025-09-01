package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
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

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Getter
@Setter
@Entity
@Table(name = "trash")
public class TrashEntityDO extends AuditableEntity implements Reflective {

    private String userId;
    private Boolean isFolder;
    private String name;
    private String md5;
    private Long size;
    private String contentType;
    /**
     * 上传时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
    /**
     * 修改时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateDate;
    /**
     * 文件路径(根路径为"/")
     */
    private String path;
    private BlobType blobType;
    @Lob
    @Column(name = "blob_data")
    private byte[] blob;

    @Column(name = "props")
    @JdbcTypeCode(SqlTypes.JSON)
    private OtherProperties props;

    private Boolean hidden;
    private Boolean move;

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}
