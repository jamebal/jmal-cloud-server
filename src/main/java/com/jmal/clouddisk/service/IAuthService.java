package com.jmal.clouddisk.service;

import com.jmal.clouddisk.util.ResponseResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * IAuthService
 *
 * @author jmal
 */
public interface IAuthService {

    /***
     * 登录
     * @param userName 用户名
     * @param passWord 密码
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> login(HttpServletRequest request, HttpServletResponse response, String userName, String passWord);

    /***
     * 登出
     * @param token token
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> logout(String token, HttpServletResponse response);

    /***
     * 检验旧密码
     * @param id
     * @param password
     * @return
     */
    ResponseResult<Object> validOldPass(String id, String password);
}
