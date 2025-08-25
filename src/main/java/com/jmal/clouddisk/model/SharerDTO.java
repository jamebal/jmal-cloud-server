package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * @author jmal
 * @Description 分享者信息
 * @Date 2021/4/6 4:25 下午
 */
@Data
@Schema
public class SharerDTO implements Reflective {
    String shareId;
    String userId;
    @Schema(name = "avatar", title = "头像链接")
    String avatar;
    @Schema(name = "showName", title = "昵称")
    String showName;
    @Schema(name = "username", title = "用户名")
    String username;
    @Schema(name = "netdiskLogo", title = "网盘logo文件名", hidden = true)
    String netdiskLogo;
    @Schema(name = "netdiskName", title = "网盘名称", hidden = true)
    String netdiskName;
    @Schema(name = "iframe", title = "iframe配置", hidden = true)
    String iframe;
}
