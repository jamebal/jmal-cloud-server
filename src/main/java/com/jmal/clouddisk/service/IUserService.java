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
    ResponseResult<Object> add(ConsumerDTO consumerDTO);

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
     * @param token token
     * @return ResponseResult
     */
    ResponseResult<ConsumerDTO> userInfo(String token);

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
     * 获取是否禁用webp状态
     * @param userId userId
     * @return disabled
     */
    boolean getDisabledWebp(String userId);

    /***
     * 获取该用户的权限信息
     * @param username username
     * @return 用户权限列表
     */
    List<String> getAuthorities(String username);

    /***
     * 获取该用户的菜单信息
     * @param userId userId
     * @return 用户菜单Id列表
     */
    List<String> getMenuIdList(String userId);

    /***
     * 根据角色获取用户名列表
     * @param roleId 角色Id
     * @return 用户名列表
     */
    List<String> getUserNameListByRole(String roleId);

    /***
     * 根据角色获取用户名列表
     * @param rolesIds 角色Id列表
     * @return 用户名列表
     */
    List<String> getUserNameListByRole(List<String> rolesIds);

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

    ConsumerDO getUserInfoById(String userId);
}
