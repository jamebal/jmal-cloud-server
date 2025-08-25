package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Description userToken
 * 用来存储用户登录后的信息
 * @Author jmal
 * @Date 2020/9/30 10:34 上午
 */
@Data
@Accessors(chain = true)
public class UserTokenDO implements Reflective {
    private String username;
    /***
     * 最后一次访问的时间戳
     */
    private long timestamp;
}
