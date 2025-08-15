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
    String USER_ID = "userId";
    String USERNAME = "username";
    String SHOW_NAME = "showName";
    /***
     * 添加用户
     * @param consumerDTO ConsumerDTO
     * @return ResponseResult<Object>
     */
    ConsumerDO add(ConsumerDTO consumerDTO);

    /***
     * 删除用户
     * @param idList userId列表
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> delete(List<String> idList);

    /***
     * 修改用户
     * @param consumerDTO ConsumerDTO
     * @param blobAvatar MultipartFile
     * @return ResponseResult
     */
    ResponseResult<Object> update(ConsumerDTO consumerDTO, MultipartFile blobAvatar);

    /***
     * 用户信息
     * @param userId userId
     * @return ResponseResult
     */
    ResponseResult<ConsumerDTO> userInfo(String userId);

    /***
     * 用户信息
     * @return ResponseResult
     */
    ResponseResult<ConsumerDTO> info();

    /***
     * 用户信息
     * @param consumerId userId
     * @return ResponseResult<Object>
     */
    ConsumerDO userInfoById(String consumerId);

    /***
     * 用户列表
     * @param queryDTO 查询用户的条件
     * @return ResponseResult
     */
    ResponseResult<List<ConsumerDTO>> userList(QueryUserDTO queryDTO);

    /***
     * 所有用户列表
     * @return 用户列表
     */
    List<ConsumerDTO> userListAll();

    /***
     * 修改用户密码
     * @param consumer ConsumerDO
     * @return ResponseResult
     */
    ResponseResult<Object> updatePass(ConsumerDO consumer);

    /***
     * 重置密码
     * @param consumer ConsumerDO
     * @return ResponseResult
     */
    ResponseResult<Object> resetPass(ConsumerDO consumer);

    /***
     * 获取用户userId
     * @param username username
     * @return userId
     */
    String getUserIdByUserName(String username);

    /**
     * 获取用户头像
     * @param username  username
     * @return 头像的文件id
     */
    String getAvatarByUsername(String username);

    /***
     * 是否有用户
     * @return ResponseResult
     */
    ResponseResult<Boolean> hasUser();

    /***
     * 初始化创建管理员
     * @param consumer ConsumerDTO
     * @return ResponseResult
     */
    ResponseResult<Object> initialization(ConsumerDTO consumer);

    /***
     * 获取用户名
     * @param userId userId
     * @return 用户名
     */
    String getUserNameById(String userId);

    /***
     * 是否禁用webp(默认开启)
     * @param userId userId
     * @param disabled disabled
     */
    void disabledWebp(String userId, Boolean disabled);

    /***
     * 判断该用户是否为创建者
     * @param userId userId
     */
    boolean getIsCreator(String userId);

    /***
     * 获取用户userId
     * @param showName 用户名
     * @return userId
     */
    String getUserIdByShowName(String showName);

    /**
     * 根据id获取用户信息
     * @param userId userId
     * @return 用户信息
     */
    ConsumerDO getUserInfoById(String userId);

    /**
     * 根据username获取用户信息
     * @param username username
     * @return 用户信息
     */
    ConsumerDO getUserInfoByUsername(String username);

    /**
     * 是否开启mfa
     * @param username username
     * @return 是否开启MFA
     */
    boolean isMfaEnabled(String username);

    void enableMfa(String userId, String secret);

    void disableMfa(String userId);

}
