package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author jmal
 * @Description 分享者信息
 * @Date 2021/4/6 4:25 下午
 */
@Data
@ApiModel
public class SharerDTO {
    String userId;
    @ApiModelProperty(name = "avatar", value = "头像链接")
    String avatar;
    @ApiModelProperty(name = "showName", value = "昵称")
    String showName;
}
