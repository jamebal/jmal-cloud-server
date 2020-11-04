package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.text.Collator;
import java.util.Comparator;

/**
 * @author jmal
 * @Description 文件类别(自定类别)
 * @Date 2020/10/26 4:19 下午
 */
@Data
public class Category implements Comparable<Category> {
    @Id
    private String id;

    private String userId;

    private String name;
    /***
     * 缩略名，默认为name
     */
    private String thumbnailName;
    /***
     * 父级分类名称
     */
    private String parentCategoryId;

    /***
     * 是否为默认分类
     */
    private Boolean isDefault;

    /***
     * 分类描述
     */
    private String desc;

    /***
     * 按分类名称排序
     * @param category Category
     * @return int
     */
    @Override
    public int compareTo(Category category) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(getName(), category.getName());
    }
}
