package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

/**
 * FileDocument 文件模型
 *
 * @blame jmal
 */
@Data
public class FileDocument {

    @Id
    private String id;

    private String userId;

    /***
     * 文件路径(根路径为"/")
     */
    private String path;

    private String rootPath;

    /***
     * 是否为文件夹
     */
    private Boolean isFolder;

    /***
     * 文件名称
     */
    private String name;
    /***
     * 文件大小
     */
    private long size;
    /***
     * 上传时间
     */
    private LocalDateTime uploadDate;
    /***
     * 修改时间
     */
    private LocalDateTime updateDate;

    /***
     * updateDate 距离现在的时间
     */
    private Long agoTime;

    /***
     * 显示大小
     */
    private String showSize;

    /***
     * 文件MD5值
     */
    private String md5;
    /***
     * 文件内容
     */
    private byte[] content;
    /***
     * 文件类型
     */
    private String contentType;
    /***
     * 文件后缀名
     */
    private String suffix;
    /***
     * 文件描述
     */
    private String description;
    /***
     * 是否收藏
     */
    private Boolean isFavorite;
    /***
     * 是否分享
     */
    private Boolean isShare;
    /***
     * 分享有效期
     */
    private Long shareExpirationDate;

}
