package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FileDocument extends FileBase{
    private String userId;
    private String username;
    private String avatar;
    /***
     * 文件路径(根路径为"/")
     */
    private String path;
    private String rootPath;
    /***
     * updateDate 距离现在的时间
     */
    private Long agoTime;

    /***
     * 显示大小
     */
    private String showSize;

    /***
     * 文件内容
     */
    private byte[] content;

    /***
     * 文件内容
     */
    private String contentText;
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

    /***
     * 分类名称
     */
    private String categoryName;

}
