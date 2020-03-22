package com.jmal.clouddisk.service;

import com.jmal.clouddisk.util.ResponseResult;

/**
 * IAuthService
 *
 * @blame jmal
 */
public interface IAuthService {

    /***
     * 登录
     * @param userName 用户名
     * @param passWord 密码(MD5加密过的)
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> login(String userName, String passWord);

    /***
     * 登出
     * @param token token
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> logout(String token);

    /***
     * 鉴权
     * @param userName 用户名
     * @param token token
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> authentication(String userName, String token);

    /***
     * 检验旧密码
     * @param id
     * @param password
     * @return
     */
    ResponseResult<Object> validOldPass(String id, String password);
}
