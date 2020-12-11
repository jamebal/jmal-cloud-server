package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;

/**
 * @author jmal
 * @Description 文章DTO
 * @Date 2020/11/26 11:13 上午
 */
@Data
@ApiModel
@Valid
public class ArticleDTO {

    @ApiModelProperty(name = "mark", value = "文章id")
    String mark;

    @ApiModelProperty(name = "userId", value = "用户Id")
    String userId;

    @ApiModelProperty(name = "isRelease", value = "是否发布")
    Boolean isRelease;

    @ApiModelProperty(name = "isAlonePage", value = "是否为独立页面")
    Boolean isAlonePage;

    @ApiModelProperty(name = "isDraft", value = "是否为草稿")
    Boolean isDraft;

    @ApiModelProperty(name = "keyword", value = "关键字")
    String keyword;

    @ApiModelProperty(name = "categoryIds", value = "分类id集合")
    String[] categoryIds;

    @ApiModelProperty(name = "tagIds", value = "标签id集合")
    String[] tagIds;

    @ApiModelProperty(name = "sortableProp", value = "排序字段")
    String sortableProp;

    @ApiModelProperty(name = "order", value = "排序的顺序")
    String order;

    @ApiModelProperty(hidden = true)
    Integer pageIndex;
    @ApiModelProperty(hidden = true)
    Integer pageSize;
}
