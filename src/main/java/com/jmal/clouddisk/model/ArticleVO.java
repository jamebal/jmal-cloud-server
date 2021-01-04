package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @Description 文章展示
 * @blame jmal
 * @Date 2020/11/15 7:33 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ArticleVO extends FileBase{

    /***
     * 用户名
     */
    private String username;
    private String avatar;
    /***
     * 文件内容
     */
    private String contentText;
    /***
     * html内容
     */
    private String html;
    /***
     * 是否发布，适用于文档类型
     */
    private Boolean release;
    /***
     * 是否为独立页，适用于文档类型
     */
    private Boolean alonePage;
    /***
     * 封面
     */
    private String cover;
    /***
     * 是否可编辑
     */
    private Boolean editable;
    /***
     * 缩略名
     */
    private String slug;
    /***
     * 分类Id集合
     */
    private String[] categoryIds;
    /***
     * 分类集合
     */
    private List<CategoryDO> categories;
    /***
     * 标签Id集合
     */
    private String[] tagIds;
    /***
     * 标签集合
     */
    private List<TagDO> tags;
}
