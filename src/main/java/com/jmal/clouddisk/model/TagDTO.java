package com.jmal.clouddisk.model;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import org.springframework.data.annotation.Id;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.text.Collator;
import java.util.Comparator;

/**
 * @author jmal
 * @Description 标签
 * @Date 2020/10/26 4:30 下午
 */
@Data
@Schema
@Valid
public class TagDTO  implements Comparable<TagDTO>{

    @Id
    @Schema(hidden = true)
    private String id;

    @Schema(name = "userId", title = "用户Id")
    private String userId;

    @NotNull(message = "标签名称不能为空")
    @Schema(name = "name", title = "标签名称")
    private String name;

    @Schema(name = "slug", title = "缩略名(默认为name)")
    private String slug;

    @Schema(name = "tagBackground", title = "标签背景")
    String tagBackground;

    @Schema(hidden = true, name = "articleNum", title = "文章数")
    Long articleNum;

    @Schema(hidden = true, name = "color", title = "标签字体颜色")
    String color;

    @Schema(hidden = true, name = "fontSize", title = "标签字体大小")
    Long fontSize;

    @Override
    public int compareTo(TagDTO tagDTO) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(getName(), tagDTO.getName());
    }
}
