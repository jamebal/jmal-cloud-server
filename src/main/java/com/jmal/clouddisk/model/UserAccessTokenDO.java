package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Description 用户授权码
 * @Author jmal
 * @Date 2020/9/30 10:34 上午
 */
@Data
@Accessors(chain = true)
public class UserAccessTokenDO {
    private String username;
    /***
     * 用户授权码
     */
    private String accessToken;
    /***
     * 该accessToken的所拥有的权限
     */
    private List<String> authorities;
}
