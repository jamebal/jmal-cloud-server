package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

/**
 * @author jmal
 * @Description 标签
 * @Date 2020/10/26 4:30 下午
 */
@Data
public class TagDO {
    @Id
    private String id;

    private String userId;
    /***
     * 标签名称
     */
    private String name;
    /***
     * 标签缩略名，默认为name
     */
    private String slug;
    /**
     * 标签颜色,十六进制字符串,例如#00000000
     */
    private String color;
    /***
     * 标签背景图
     */
    String tagBackground;

    public ArticlesQueryVO toArticlesQuery(){
        ArticlesQueryVO articlesQueryVO = new ArticlesQueryVO();
        articlesQueryVO.setBackground(tagBackground);
        articlesQueryVO.setName("标签 - "+name);
        return articlesQueryVO;
    }
}
