package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;

import javax.validation.Valid;

/**
 * @author jmal
 * @Description 标签
 * @Date 2020/10/26 4:30 下午
 */
@Data
@ApiModel
@Valid
public class TagDTO {

    @Id
    @ApiModelProperty(hidden = true)
    private String id;

    private String userId;

    private String name;
    /***
     * 缩略名，默认为name
     */
    private String thumbnailName;
}
