package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description 用户授权码
 * @Author jmal
 * @Date 2020/9/30 10:34 上午
 */
@Data
public class UserAccessTokenDTO implements Reflective {
    String id;
    /***
     * 授权码名称
     */
    private String name;
    /***
     * 用户账号
     */
    private String username;
    /***
     * 创建时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(hidden = true)
    LocalDateTime createTime;
    /***
     * 最近活动时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(hidden = true)
    LocalDateTime lastActiveTime;
}
