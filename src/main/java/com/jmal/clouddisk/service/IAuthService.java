package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.LdapConfigDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.util.ResponseResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ldap.core.LdapTemplate;

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
     * 更新ldap配置
     * @param ldapConfigDTO ldapConfigDTO
     * @return ResponseResult
     */
    ResponseResult<Object> updateLdapConfig(LdapConfigDTO ldapConfigDTO);

    /**
     * 测试ldap连接配置
     * @param ldapConfigDTO ldapConfigDTO
     * @return ResponseResult
     */
    LdapTemplate testLdapConfig(LdapConfigDTO ldapConfigDTO);

    /**
     * 获取ldap配置
     * @return LdapConfigDTO
     */
    LdapConfigDTO loadLdapConfig();
}
