package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.LdapConfigDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.util.ResponseResult;
import jakarta.servlet.http.HttpServletResponse;

/**
 * IAuthService
 *
 * @author jmal
 */
public interface IAuthService {

    /**
     * 登录
     * @param response HttpServletResponse
     * @param userDTO ConsumerDTO
     * @return ResponseResult
     */
    ResponseResult<Object> login(HttpServletResponse response, ConsumerDTO userDTO);

    /**
     * ldap登录
     * @param response HttpServletResponse
     * @param username username
     * @param password password
     * @return ResponseResult
     */
    ResponseResult<Object> ldapLogin(HttpServletResponse response, String username, String password);

    /**
     * 登出
     * @param token token
     * @param response HttpServletResponse
     * @return ResponseResult
     */
    ResponseResult<Object> logout(String token, HttpServletResponse response);

    /**
     * 检验旧密码
     * @param userId userId
     * @param password password
     * @return ResponseResult
     */
    ResponseResult<Object> validOldPass(String userId, String password);

    /**
     * 修改添加ldap配置
     * @param ldapConfigDTO ldapConfigDTO
     * @return ResponseResult
     */
    ResponseResult<Object> ldapConfig(LdapConfigDTO ldapConfigDTO);
}
