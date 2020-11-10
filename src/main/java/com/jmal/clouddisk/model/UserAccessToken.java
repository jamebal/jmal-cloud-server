package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Description UserAccessToken
 * @Author jmal
 * @Date 2020/9/30 10:34 上午
 */
@Data
@Accessors(chain = true)
public class UserAccessToken {
    private String username;
    private String accessToken;
}
