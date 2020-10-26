package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

/**
 * @author jmal
 * @Description 文件类别(自定类别)
 * @Date 2020/10/26 4:19 下午
 */
@Data
public class Category {
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
    private String parentCategoryName;
    /***
     * 分类描述
     */
    private String desc;
}
