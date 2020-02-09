package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;

/**
 * @Description 用户模型
 * @blame jmal
 */
@Data
@ApiModel
public class User {
    @ApiModelProperty(hidden = true)
    @Id
    String id;
    @ApiModelProperty(value = "用户名", example = "jmal")
    String username;
    @ApiModelProperty(value = "显示用户名", example = "best jmal")
    String showName;
    @ApiModelProperty(value = "密码", example = "123456")
    String password;
    /***
     * 头像
     */
    @ApiModelProperty(value = "头像", example = "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif")
    String avatar;
    /***
     * 介绍
     */
    String introduction;
    String[] roles;
    /***
     * 默认配额, 10G
     */
    @ApiModelProperty(value = "默认配额",example = "10")
    int quota;
}
