package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.tomcat.jni.File;

import java.util.List;

/**
 * @Description 文档
 * @blame jmal
 * @Date 2020/11/15 7:33 下午
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MarkdownVO extends FileBase {
    private String userId;
    private String username;
    /***
     * updateDate 距离现在的时间
     */
    private Long agoTime;
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
    private String slug;
    /***
     * 用户头像
     */
    private String avatar;
    /***
     * 分类Id集合
     */
    private String[] categoryIds;

    /***
     * 分类集合
     */
    private List<Category> categories;

}
