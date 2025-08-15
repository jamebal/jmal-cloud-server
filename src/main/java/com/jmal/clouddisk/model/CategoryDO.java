package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.text.Collator;
import java.util.Comparator;

/**
 * @author jmal
 * @Description 类别(自定类别)
 * @Date 2020/10/26 4:19 下午
 */
@Data
public class CategoryDO implements Comparable<CategoryDO>, Reflective {
    @Id
    private String id;
    /***
     * 用户Id
     */
    private String userId;
    /***
     * 分类名称
     */
    private String name;
    /***
     * 缩略名，默认为name
     */
    private String slug;
    /***
     * 父级分类Id
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
}
