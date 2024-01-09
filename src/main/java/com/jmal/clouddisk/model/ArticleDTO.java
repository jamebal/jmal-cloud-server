package com.jmal.clouddisk.model;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import jakarta.validation.Valid;

/**
 * @author jmal
 * @Description 文章DTO
 * @Date 2020/11/26 11:13 上午
 */
@Data
@Schema
@Valid
public class ArticleDTO {

    @Schema(name = "mark", title = "文章id")
    String mark;

    @Schema(name = "userId", title = "用户Id")
    String userId;

    @Schema(name = "isRelease", title = "是否发布")
    Boolean isRelease;

    @Schema(name = "isAlonePage", title = "是否为独立页面")
    Boolean isAlonePage;

    @Schema(name = "isDraft", title = "是否为草稿")
    Boolean isDraft;

    @Schema(name = "keyword", title = "关键字")
    String keyword;

    @Schema(name = "categoryIds", title = "分类id集合")
    String[] categoryIds;

    @Schema(name = "tagIds", title = "标签id集合")
    String[] tagIds;

    @Schema(name = "sortableProp", title = "排序字段")
    String sortableProp;

    @Schema(name = "order", title = "排序的顺序")
    String order;

    @Schema(hidden = true, name = "pageIndex", title = "当前页数")
    Integer pageIndex;
    @Schema(hidden = true, name = "pageSize", title = "每页条数")
    Integer pageSize;
}
