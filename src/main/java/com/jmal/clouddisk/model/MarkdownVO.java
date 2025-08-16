package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Description 文档
 * @blame jmal
 * @Date 2020/11/15 7:33 下午
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MarkdownVO extends MarkdownBaseFile implements Reflective {

    private String userId;
    /***
     * updateDate 距离现在的时间
     */
    private Long agoTime;
    /***
     * 是否有草稿
     */
    private Boolean draft;

}
