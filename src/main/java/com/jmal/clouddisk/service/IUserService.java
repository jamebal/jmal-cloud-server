package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.Consumer;
import com.jmal.clouddisk.util.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * IConsumerService
 *
 * @author jmal
 */
public interface IUserService {
    /***
     * 添加用户
     * @param consumer
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> add(Consumer consumer);

    /***
     * 删除用户
     * @param id id
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> delete(String id);

    /***
     * 修改用户
     * @param consumer
     * @param blobAvatar
     * @return
     */
    ResponseResult<Object> update(Consumer consumer, MultipartFile blobAvatar);

    /***
     * 用户信息
     * @param token
     * @param takeUpSpace 是否显示占用空间
     * @param returnPassWord
     * @return
     */
    ResponseResult<Object> userInfo(String token,Boolean takeUpSpace,Boolean returnPassWord);

    /***
     * 用户信息
     * @param consumerId
     * @return ResponseResult<Object>
     */
    Consumer userInfoById(String consumerId);

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

    /***
     * 获取用户userId
     * @param username
     * @return
     */
    String getUserIdByUserName(String username);

    /***
     * 是否有用户
     * @return
     */
    ResponseResult<Boolean> hasUser();

    /***
     * 初始化创建管理员
     * @param consumer
     * @return
     */
    ResponseResult<Object> initialization(Consumer consumer);

    /***
     * 获取用户名
     * @param userId
     * @return
     */
    String getUserNameById(String userId);
}
