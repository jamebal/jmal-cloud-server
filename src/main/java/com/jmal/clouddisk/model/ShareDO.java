package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @Description 文件分享模型
 * @Author jmal
 * @Date 2020-03-17 16:28
 */
@Data
public class ShareDO {
    @Id
    private String id;
    private Boolean shareBase;
    /***
     * 链接拥有者
     */
    private String userId;
    /***
     * 文件Id
     */
    private String fileId;
    /***
     * 文件名
     */
    private String fileName;
    /***
     * 文件类型
     */
    private String contentType;
    /***
     * 是否为文件夹
     */
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
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime expireDate;
    /***
     * 是否为私密链接
     */
    private Boolean isPrivacy;
    /***
     * 提取码
     */
    private String extractionCode;
    /***
     * 禁止上传
     */
    private Boolean notAllowedDownLoad;

    /***
     * 允许下载
     */
    private Boolean allowedUpload;

}
