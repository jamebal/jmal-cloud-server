package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.rbac.ConsumerDTO;
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
     * @param userDTO ConsumerDTO
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> login(HttpServletRequest request, HttpServletResponse response, ConsumerDTO userDTO);

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
