package com.jmal.clouddisk.model;

import com.jmal.clouddisk.service.impl.FileServiceImpl;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = FileServiceImpl.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "name_1", def = "{'name': 1}"),
        @CompoundIndex(name = "name_-1", def = "{'name': -1}"),
        @CompoundIndex(name = "size_1", def = "{'size': 1}"),
        @CompoundIndex(name = "size_-1", def = "{'size': -1}"),
        @CompoundIndex(name = "updateDate_1", def = "{'updateDate': 1}"),
        @CompoundIndex(name = "updateDate_-1", def = "{'updateDate': -1}"),
        @CompoundIndex(name = "user_path", def = "{'userId': 1, 'path': 1}"),
        @CompoundIndex(name = "user_isFolder", def = "{'userId': 1, 'isFolder': 1}"),
        @CompoundIndex(name = "user_isFavorite", def = "{'userId': 1, 'isFavorite': 1}"),
        @CompoundIndex(name = "user_contentType", def = "{'userId': 1, 'contentType': 1}"),
        @CompoundIndex(name = "user_isFolder_path", def = "{'userId': 1, 'isFolder': 1, 'path': 1}"),
})
public class FileDocument extends FileBase{
    private String userId;
    private String username;
    /***
     * 文件头像/缩略图
     */
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
     * 图片的宽度
     */
    private String w;
    /***
     * 图片的高度
     */
    private String h;
    /***
     * 文件内容
     */
    private byte[] content;

    /***
     * 文件内容
     */
    private String contentText;
    /***
     * html内容
     */
    private String html;
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
    /***
     * 音乐
     */
    private Music music;
    /***
     * 是否发布，适用于文档类型
     */
    private Boolean release;
    /***
     * 是否为独立页，适用于文档类型
     */
    private Boolean alonePage;
    /***
     * 独立页排序
     */
    private Integer pageSort;
    /***
     * 是否有草稿
     */
    private FileDocument draft;
    /***
     * 封面
     */
    private String cover;
    /***
     * 缩略名
     */
    private String slug;
    /***
     * 分类Id集合
     */
    private String[] categoryIds;
    /***
     * 标签Id集合
     */
    private String[] tagIds;
}
