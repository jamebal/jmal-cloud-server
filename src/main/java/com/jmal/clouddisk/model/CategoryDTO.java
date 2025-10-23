package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;

/**
 * @Description 文件类别DTO
 * @Author jmal
 * @Date 2020/10/27 10:48 下午
 */
@Data
@Schema
@Valid
public class CategoryDTO implements Comparable<CategoryDTO>, Reflective {

    @Id
    @Schema(hidden = true)
    private String id;

    @Schema(name = "userId", title = "用户id")
    private String userId;

    @NotNull(message = "分类名称不能为空")
    @Schema(name = "name", title = "分类名称", example = "新建分类", requiredMode = Schema.RequiredMode.REQUIRED)
    @Column(unique = true, nullable = false)
    private String name;

    /***
     * 缩略名，默认为name
     */
    @Schema(name = "slug", title = "分类缩略名")
    @Column(unique = true, nullable = false)
    private String slug;
    /***
     * 父级分类Id
     */
    @Schema(name = "parentCategoryId", title = "父级分类Id")
    private String parentCategoryId;
    /***
     * 子分类数
     */
    @Schema(hidden = true)
    private Integer subCategorySize;
    /***
     * 是否为默认分类
     */
    @Schema(hidden = true)
    private Boolean isDefault;
    /***
     * 分类描述
     */
    @Schema(name = "desc", title = "分类描述")
    private String desc;
    /***
     * 文章数
     */
    @Schema(hidden = true)
    private Integer articleNum;
    /***
     * 文章总数
     */
    @Schema(hidden = true)
    private Integer value;
    /***
     * 子分类
     */
    @Schema(hidden = true)
    private List<CategoryDTO> children;

    @Schema(name = "categoryBackground", title = "分类背景")
    String categoryBackground;

    @Schema(hidden = true, name = "categoryIdsList", title = "分类Id列表")
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
