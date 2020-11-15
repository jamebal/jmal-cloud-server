package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

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
     * 是否发布，适用于文档类型
     */
    private Boolean release;
    /***
     * 是否有草稿
     */
    private Boolean draft;
    /***
     * 封面
     */
    private String cover;

    /***
     * 分类Id集合
     */
    private String[] categoryIds;

}
