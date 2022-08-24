package com.jmal.clouddisk.model;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * @author jmal
 * @Description 分享者信息
 * @Date 2021/4/6 4:25 下午
 */
@Data
@Schema
public class SharerDTO {
    String userId;
    @Schema(name = "avatar", title = "头像链接")
    String avatar;
    @Schema(name = "showName", title = "昵称")
    String showName;
    @Schema(name = "username", title = "用户名")
    String username;
}
