package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 文件分享模型
 * @Author jmal
 * @Date 2020-03-17 16:28
 */
@Getter
@Setter
@Document(collection = "share")
@Entity
@Table(name = "share")
public class ShareDO extends AuditableEntity implements Reflective {
    /**
     * 父级分享Id
     */
    @Column(length = 24)
    private String fatherShareId;
    @Column(length = 24)
    private String shortId;
    private Boolean shareBase;
    /**
     * 链接拥有者
     */
    @Column(length = 24)
    private String userId;
    /**
     * 文件Id
     */
    private String fileId;
    /**
     * 文件名
     */
    private String fileName;
    /**
     * 文件类型
     */
    @Column(length = 128)
    private String contentType;
    /**
     * 是否为文件夹
     */
    private Boolean isFolder;
    /**
     * 创建时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createDate;
    /**
     * 过期时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime expireDate;
    /**
     * 是否为私密链接
     */
    private Boolean isPrivacy;
    /**
     * 提取码
     */
    @Column(length = 8)
    private String extractionCode;
    /**
     * 操作权限
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<OperationPermission> operationPermissionList;

}
