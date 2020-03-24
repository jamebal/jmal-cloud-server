package com.jmal.clouddisk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * @Description 用户模型
 * @blame jmal
 */
@Data
@ApiModel
public class Consumer {

    @Id
    String id;
    @ApiModelProperty(value = "用户名", example = "jmal")
    String username;
    @ApiModelProperty(value = "显示用户名", example = "best jmal")
    String showName;
    @ApiModelProperty(value = "密码", example = "123456")
    String password;

    Long createTime;
    /***
     * 头像
     */
    @ApiModelProperty(value = "头像", example = "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif")
    String avatar;

    /***
     * 标语
     */
    String slogan;

    /***
     * 简介
     */
    String introduction;

    /***
     * 角色
     */
    String[] roles;
    /***
     * 默认配额, 10G
     */
    @ApiModelProperty(value = "默认配额",example = "10")
    Integer quota;

    /***
     * 已使用的空间
     */
    Long takeUpSpace;

}
