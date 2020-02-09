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
     * @return ResponseResult
     */
    ResponseResult login(String userName, String passWord);

    /***
     * 登出
     * @param token token
     * @return ResponseResult
     */
    ResponseResult logout(String token);

    /***
     * 鉴权
     * @param userName 用户名
     * @param token token
     * @return ResponseResult
     */
    ResponseResult authentication(String userName, String token);
}
