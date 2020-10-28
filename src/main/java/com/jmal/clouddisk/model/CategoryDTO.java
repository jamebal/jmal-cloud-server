package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.util.StringUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.text.Collator;
import java.util.Comparator;

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

    @NotNull(message = "用户id不能为空")
    @ApiModelProperty(name = "userId", value = "用户id", required = true , example = "5e2d6675aab5fa4b7fecb59b")
    private String userId;

    @NotNull(message = "分类名称不能为空")
    @ApiModelProperty(name = "name", value = "分类名称", required = true , example = "新建分类")
    private String name;

    /***
     * 缩略名，默认为name
     */
    @ApiModelProperty(name = "thumbnailName", value = "分类缩略名")
    private String thumbnailName;
    /***
     * 父级分类名称
     */
    @ApiModelProperty(name = "parentCategoryName", value = "父级分类名称")
    private String parentCategoryName;
    /***
     * 子分类数
     */
    @ApiModelProperty(hidden = true)
    private Integer subCategorySize;
    /***
     * 分类描述
     */
    @ApiModelProperty(name = "desc", value = "分类描述")
    private String desc;

    public void setParentCategoryName(String parentCategoryName) {
        if(!StringUtils.isEmpty(parentCategoryName)) {
            this.parentCategoryName = parentCategoryName;
        }
    }

    /***
     * 按照分类名称来排序
     * @param categoryDTO
     * @return
     */
    @Override
    public int compareTo(CategoryDTO categoryDTO) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(getName(), categoryDTO.getName());
    }
}
