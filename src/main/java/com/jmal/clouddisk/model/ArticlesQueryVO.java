package com.jmal.clouddisk.model;

import lombok.Data;

/**
 * @author jmal
 * @Description 文章查询页简要信息
 * @Date 2020/12/16 3:12 下午
 */
 @Data
public class ArticlesQueryVO {
    /***
     * 文章标题名
     */
    String name;
    /***
     * 文章背景
     */
    String background;
}
