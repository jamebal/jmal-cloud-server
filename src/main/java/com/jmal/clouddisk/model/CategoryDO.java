package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.service.impl.CategoryService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.text.Collator;
import java.util.Comparator;

/**
 * @author jmal
 * @Description 类别(自定类别)
 * @Date 2020/10/26 4:19 下午
 */
@Getter
@Setter
@Document(collection = CategoryService.COLLECTION_NAME)
@Entity
@Table(name = CategoryService.COLLECTION_NAME)
public class CategoryDO extends AuditableEntity implements Comparable<CategoryDO>, Reflective {
    /***
     * 用户Id
     */
    @Column(length = 24)
    private String userId;
    /***
     * 分类名称
     */
    @Column(length = 32)
    private String name;
    /***
     * 缩略名，默认为name
     */
    @Column(length = 24)
    private String slug;
    /***
     * 父级分类Id
     */
    @Column(length = 24)
    private String parentCategoryId;
    /***
     * 是否为默认分类
     */
    private Boolean isDefault;
    /***
     * 分类描述
     */
    @Column(name = "description")
    private String desc;
    /***
     * 分类背景
     */
    String categoryBackground;
    /***
     * 按分类名称排序
     * @param categoryDO Category
     * @return int
     */
    @Override
    public int compareTo(CategoryDO categoryDO) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(getName(), categoryDO.getName());
    }

    /***
     * 按类别显示的文章查询页简要信息
     * @return 文章查询页简要信息
     */
    public ArticlesQueryVO toArticlesQuery(){
        ArticlesQueryVO articlesQueryVO = new ArticlesQueryVO();
        articlesQueryVO.setBackground(categoryBackground);
        articlesQueryVO.setName("分类 - "+name);
        return articlesQueryVO;
    }

    public void updateFields(CategoryDO category) {
        if (category == null) {
            return;
        }
        if (category.getUserId() != null) {
            this.setUserId(category.getUserId());
        }
        if (category.getName() != null) {
            this.setName(category.getName());
        }
        if (category.getSlug() != null) {
            this.setSlug(category.getSlug());
        }
        if (category.getDesc() != null) {
            this.setDesc(category.getDesc());
        }
        if (category.getCategoryBackground() != null) {
            this.setCategoryBackground(category.getCategoryBackground());
        }
        if (category.getParentCategoryId() != null) {
            this.setParentCategoryId(category.getParentCategoryId());
        }
        if (category.getIsDefault() != null) {
            this.setIsDefault(category.getIsDefault());
        }
    }
}
