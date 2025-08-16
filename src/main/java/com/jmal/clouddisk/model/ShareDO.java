package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.service.impl.ShareServiceImpl;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 文件分享模型
 * @Author jmal
 * @Date 2020-03-17 16:28
 */
@Data
@Document(collection = ShareServiceImpl.COLLECTION_NAME)
public class ShareDO implements Reflective {
    @Id
    private String id;
    /**
     * 父级分享Id
     */
    private String fatherShareId;
    private String shortId;
    private Boolean shareBase;
    /**
     * 链接拥有者
     */
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
    private String extractionCode;
    /**
     * 操作权限
     */
    private List<OperationPermission> operationPermissionList;

}
