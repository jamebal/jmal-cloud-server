package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.User;
import com.jmal.clouddisk.util.ResponseResult;

/**
 * IUserService
 *
 * @blame jmal
 */
public interface IUserService {
    /***
     * 添加用户
     * @param user user
     * @return ResponseResult
     */
    ResponseResult add(User user);

    /***
     * 删除用户
     * @param id id
     * @return ResponseResult
     */
    ResponseResult delete(String id);

    /***
     * 修改用户
     * @param user user
     * @return ResponseResult
     */
    ResponseResult update(User user);

    /***
     * 用户信息
     * @param token token
     * @return ResponseResult
     */
    ResponseResult userInfo(String token);

    /***
     * 用户列表
     * @return ResponseResult
     */
    ResponseResult userList();

    /***
     * 验证用户名
     * @param token
     * @return
     */
    String getUserName(String token);

//    /***
//     * 添加用户组
//     * @param user user
//     * @return ResponseResult
//     */
//    ResponseResult addGroup(User user);
//
//    /***
//     * 删除用户组
//     * @param id id
//     * @return ResponseResult
//     */
//    ResponseResult deleteGroup(String id);
//
//    /***
//     * 修改用户组
//     * @param user user
//     * @return ResponseResult
//     */
//    ResponseResult updateGroup(User user);
//
//    /***
//     * 用户组信息
//     * @param id id
//     * @return ResponseResult
//     */
//    ResponseResult groupInfo(String id);
//
//    /***
//     * 用户列表
//     * @return ResponseResult
//     */
//    ResponseResult groupList();
}
