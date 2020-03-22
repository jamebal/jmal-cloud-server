package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.Consumer;
import com.jmal.clouddisk.util.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * IConsumerService
 *
 * @blame jmal
 */
public interface IUserService {
    /***
     * 添加用户
     * @param Consumer Consumer
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> add(Consumer Consumer);

    /***
     * 删除用户
     * @param id id
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> delete(String id);

    /***
     * 修改用户
     * @param Consumer Consumer
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> update(Consumer Consumer, MultipartFile blobAvatar);

    /***
     * 用户信息
     * @param token
     * @param takeUpSpace 是否显示占用空间
     * @return
     */
    ResponseResult<Object> userInfo(String token,Boolean takeUpSpace,Boolean returnPassWord);

    /***
     * 用户信息
     * @param ConsumerId ConsumerId
     * @return ResponseResult<Object>
     */
    Consumer userInfoById(String ConsumerId);

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

    /***
     * 修改用户密码
     * @param consumer
     * @return
     */
    ResponseResult<Object> updatePass(Consumer consumer);

    /***
     * 重置密码
     * @param consumer
     * @return
     */
    ResponseResult<Object> resetPass(Consumer consumer);

//    /***
//     * 添加用户组
//     * @param Consumer Consumer
//     * @return ResponseResult<Object>
//     */
//    ResponseResult<Object> addGroup(Consumer Consumer);
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
//     * @param Consumer Consumer
//     * @return ResponseResult<Object>
//     */
//    ResponseResult<Object> updateGroup(Consumer Consumer);
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
