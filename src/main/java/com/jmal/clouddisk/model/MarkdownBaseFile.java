package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author jmal
 * @Description MarkdownBaseFile
 * @date 2023/5/10 13:54
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MarkdownBaseFile extends FileBase {

    private String username;
    /***
     * 是否发布，适用于文档类型
     */
    private Boolean release;
    /***
     * 封面
     */
    private String cover;
    /***
     * 用户头像
     */
    private String avatar;
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
