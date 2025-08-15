package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;

import java.util.List;

/**
 * @Description 用户登录信息
 * @blame jmal
 * @Date 2021/1/9 2:13 下午
 */
@Data
public class UserLoginContext implements Reflective {
    String userId;
    String username;
    /***
     * 用户权限信息
     */
    List<String> authorities;
}
