package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel
@Valid
public class TagDTO  implements Comparable<TagDTO>{

    @Id
    @ApiModelProperty(hidden = true)
    private String id;

    @ApiModelProperty(name = "userId", value = "用户Id")
    private String userId;

    @NotNull(message = "标签名称不能为空")
    @ApiModelProperty(name = "name", value = "标签名称")
    private String name;

    @ApiModelProperty(name = "slug", value = "缩略名(默认为name)")
    private String slug;

    @ApiModelProperty(name = "tagBackground", value = "标签背景")
    String tagBackground;

    @ApiModelProperty(hidden = true, name = "articleNum", value = "文章数")
    Long articleNum;

    @Override
    public int compareTo(TagDTO tagDTO) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(getName(), tagDTO.getName());
    }
}
