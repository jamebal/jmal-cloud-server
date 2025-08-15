package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description 用户授权码
 * @Author jmal
 * @Date 2020/9/30 10:34 上午
 */
@Data
public class UserAccessTokenDO implements Reflective {
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
     * 用户授权码
     */
    private String accessToken;
    /***
     * 创建时间
     */
    LocalDateTime createTime;
    /***
     * 最近活动时间
     */
    LocalDateTime lastActiveTime;
}
