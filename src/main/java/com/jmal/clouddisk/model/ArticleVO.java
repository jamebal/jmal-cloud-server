package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Description 文章展示
 * @blame jmal
 * @Date 2020/11/15 7:33 下午
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleVO extends MarkdownBaseFile implements Reflective {
    /***
     * 文件内容
     */
    private String contentText;
    /***
     * html内容
     */
    private String html;
    /***
     * 是否为独立页，适用于文档类型
     */
    private Boolean alonePage;
    /***
     * 是否可编辑
     */
    private Boolean editable;

}
