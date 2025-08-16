package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.Id;

import java.text.Collator;
import java.util.Comparator;

/**
 * @author jmal
 * @Description 标签
 * @Date 2020/10/26 4:30 下午
 */
@Data
public class TagDO implements Comparable<TagDO>, Reflective {
    @Id
    private String id;

    private String userId;
    /**
     * 标签名称
     */
    private String name;
    /**
     * 标签缩略名，默认为name
     */
    private String slug;
    /**
     * 标签颜色,十六进制字符串,例如#00000000
     */
    private String color;
    /**
     * 标签背景图
     */
    String tagBackground;
    /**
     * 排序
     */
    Integer sort;

    public ArticlesQueryVO toArticlesQuery() {
        ArticlesQueryVO articlesQueryVO = new ArticlesQueryVO();
        articlesQueryVO.setBackground(tagBackground);
        articlesQueryVO.setName("标签 - " + name);
        return articlesQueryVO;
    }

    public TagDTO toTagDTO() {
        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(id);
        tagDTO.setUserId(userId);
        tagDTO.setName(name);
        tagDTO.setSlug(slug);
        tagDTO.setTagBackground(tagBackground);
        tagDTO.setColor(color);
        return tagDTO;
    }

    @Override
    public int compareTo(@org.jetbrains.annotations.NotNull TagDO tagDO) {
        if (this.sort == null || tagDO.sort == null) {
            return sort(tagDO);
        }
        return this.sort - tagDO.sort;
    }

    private int sort(@NotNull TagDO tagDO) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(getName(), tagDO.getName());
    }
}
