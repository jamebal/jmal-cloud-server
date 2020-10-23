package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Data
public class FileDocument {

    @Id
    private String id;

    private String userId;

    private String username;
    private String avatar;

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
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy 年 MM 月 dd 日")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
    /***
     * 上传时间
     */
    private String uploadTime;
    /***
     * 修改时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy 年 MM 月 dd 日")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
     * 文件内容
     */
    private String contentText;

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

    private Music music;

    /***
     * 封面
     */
    private String cover;

}
