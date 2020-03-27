package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @Description share
 * @Author jmal
 * @Date 2020-03-17 16:28
 */
@Data
public class ShareBO {

    @Id
    private String id;

    /***
     * 链接拥有者
     */
    private String userId;
    /***
     * 文件Id
     */
    private String fileId;

    private String fileName;

    private String contentType;

    private Boolean isFolder;
    /***
     * 创建时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createDate;
    /***
     * 过期时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireDate;

    /***
     * 禁止上传
     */
    private Boolean notAllowedDownLoad;

    /***
     * 允许下载
     */
    private Boolean allowedUpload;
}
