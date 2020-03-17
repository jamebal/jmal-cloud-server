package com.jmal.clouddisk.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;

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
    /***
     * 创建时间
     */
    private LocalDateTime createDate;
    /***
     * 过期时间
     */
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
