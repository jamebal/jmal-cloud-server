package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;

/**
 * @Description 文件类别DTO
 * @blame jmal
 * @Date 2020/10/27 10:48 下午
 */
@Data
@ApiModel
@Valid
public class CategoryDTO implements Comparable<CategoryDTO> {

    @Id
    @ApiModelProperty(hidden = true)
    private String id;

    @ApiModelProperty(name = "userId", value = "用户id")
    private String userId;

    @NotNull(message = "分类名称不能为空")
    @ApiModelProperty(name = "name", value = "分类名称", required = true , example = "新建分类")
    private String name;

    /***
     * 缩略名，默认为name
     */
    @ApiModelProperty(name = "slug", value = "分类缩略名")
    private String slug;
    /***
     * 父级分类Id
     */
    @ApiModelProperty(name = "parentCategoryId", value = "父级分类Id")
    private String parentCategoryId;
    /***
     * 子分类数
     */
    @ApiModelProperty(hidden = true)
    private Integer subCategorySize;
    /***
     * 是否为默认分类
     */
    @ApiModelProperty(hidden = true)
    private Boolean isDefault;
    /***
     * 分类描述
     */
    @ApiModelProperty(name = "desc", value = "分类描述")
    private String desc;
    /***
     * 文章数
     */
    @ApiModelProperty(hidden = true)
    private Integer articleNum;
    /***
     * 文章总数
     */
    @ApiModelProperty(hidden = true)
    private Integer value;
    /***
     * 子分类
     */
    @ApiModelProperty(hidden = true)
    private List<CategoryDTO> children;

    @ApiModelProperty(name = "categoryBackground", value = "分类背景")
    String categoryBackground;

    @ApiModelProperty(hidden = true)
    private List<List<String>> categoryIdsList;
    /***
     * 按照分类名称来排序
     * @param categoryDTO CategoryDTO
     * @return int
     */
    @Override
    public int compareTo(CategoryDTO categoryDTO) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(getName(), categoryDTO.getName());
    }
}
