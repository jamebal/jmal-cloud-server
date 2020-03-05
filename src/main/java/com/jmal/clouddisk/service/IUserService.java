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
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> add(User user);

    /***
     * 删除用户
     * @param id id
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> delete(String id);

    /***
     * 修改用户
     * @param user user
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> update(User user);

    /***
     * 用户信息
     * @param token token
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> userInfo(String token);

    /***
     * 用户信息
     * @param userId userId
     * @return ResponseResult<Object>
     */
    User userInfoById(String userId);

    /***
     * 用户列表
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> userList();

    /***
     * 验证用户名
     * @param token
     * @return
     */
    String getUserName(String token);

//    /***
//     * 添加用户组
//     * @param user user
//     * @return ResponseResult<Object>
//     */
//    ResponseResult<Object> addGroup(User user);
//
//    /***
//     * 删除用户组
//     * @param id id
//     * @return ResponseResult<Object>
//     */
//    ResponseResult<Object> deleteGroup(String id);
//
//    /***
//     * 修改用户组
//     * @param user user
//     * @return ResponseResult<Object>
//     */
//    ResponseResult<Object> updateGroup(User user);
//
//    /***
//     * 用户组信息
//     * @param id id
//     * @return ResponseResult<Object>
//     */
//    ResponseResult<Object> groupInfo(String id);
//
//    /***
//     * 用户列表
//     * @return ResponseResult<Object>
//     */
//    ResponseResult<Object> groupList();
}
