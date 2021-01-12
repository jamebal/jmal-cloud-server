package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.model.rbac.UserLoginContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description 用户登录信息
 * @blame jmal
 * @Date 2021/1/9 2:13 下午
 */
@Service
public class UserLoginHolder {

    /***
     * 获取当前用户信息
     * @return UserLoginContext
     */
    public UserLoginContext getCurrentUser(){
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(requestAttributes == null){
            UserLoginContext userLoginContext = new UserLoginContext();
            userLoginContext.setAuthorities(new ArrayList<>());
            return userLoginContext;
        }
        Object object = requestAttributes.getAttribute("user", 0);
        if(object != null){
            return (UserLoginContext) object;
        } else {
            UserLoginContext userLoginContext = new UserLoginContext();
            userLoginContext.setAuthorities(new ArrayList<>());
            return userLoginContext;
        }
    }

    /***
     * 获取当前用户权限
     * @return 权限列表
     */
    public List<String> getAuthorities(){
        return getCurrentUser().getAuthorities();
    }

    /***
     * 获取当前用户名
     * @return username
     */
    public String getUsername(){
        return getCurrentUser().getUsername();
    }

    /***
     * 获取当前用户Id
     * @return userId
     */
    public String getUserId(){
        return getCurrentUser().getUserId();
    }
}
