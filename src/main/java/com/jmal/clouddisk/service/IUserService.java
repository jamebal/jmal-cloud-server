package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.util.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * IConsumerService
 *
 * @author jmal
 */
public interface IUserService {
    /***
     * 添加用户
     * @param consumerDTO
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> add(ConsumerDTO consumerDTO);

    /***
     * 删除用户
     * @param idList
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> delete(List<String> idList);

    /***
     * 修改用户
     * @param consumerDTO
     * @param blobAvatar
     * @return
     */
    ResponseResult<Object> update(ConsumerDTO consumerDTO, MultipartFile blobAvatar);

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
    ConsumerDO userInfoById(String consumerId);

    /***
     * 用户列表
     * @param queryDTO
     * @return
     */
    ResponseResult<List<ConsumerDTO>> userList(QueryUserDTO queryDTO);

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
    ResponseResult<Object> updatePass(ConsumerDO consumer);

    /***
     * 重置密码
     * @param consumer
     * @return
     */
    ResponseResult<Object> resetPass(ConsumerDO consumer);

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
    ResponseResult<Object> initialization(ConsumerDO consumer);

    /***
     * 获取用户名
     * @param userId
     * @return
     */
    String getUserNameById(String userId);

    /***
     * 是否禁用webp(默认开启)
     * @param userId userId
     * @param disabled disabled
     */
    void disabledWebp(String userId, Boolean disabled);

    /***
     * 获取是否禁用webp状态
     * @param userId userId
     * @return disabled
     */
    boolean getDisabledWebp(String userId);

    /***
     * 获取当前用户权限
     * @return 用户权限列表
     */
    List<String> getCurrentUserAuthorities();
}
