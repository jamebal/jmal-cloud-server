package com.jmal.clouddisk.repository;

import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.model.UserTokenDO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;

import java.util.List;

/**
 * IAuthDAO
 * @author jmal
 */
public interface IAuthDAO {

    /**
     * 数据源
     * @return DataSource
     */
    DataSource getDataSource();

    /**
     * 获取用户上次缓存的token的时间戳
     * @param username 用户名
     * @return UserToken
     */
    UserTokenDO findOneUserToken(String username);

    /***
     * 更新用户token的时间戳
     * @param username 用户名
     */
    void updateToken(String username);

    /***
     * 根据AccessToken获取用户名
     * @param accessToken accessToken
     * @return UserAccessTokenDO
     */
    UserAccessTokenDO getUserNameByAccessToken(String accessToken);

    /***
     * 创建accessToken
     * @param userAccessTokenDO userAccessTokenDO
     */
    void generateAccessToken(UserAccessTokenDO userAccessTokenDO);

    /***
     * 删除用户的token
     * @param userList
     */
    void deleteAllByUser(List<ConsumerDO> userList);

    /***
     * accessToken列表
     * @param username 用户名
     * @return List<UserAccessTokenDTO>
     */
    List<UserAccessTokenDTO> accessTokenList(String username);

    /***
     * 更新accessToken最近访问时间
     * @param username 用户名
     */
    void updateAccessToken(String username);

    /***
     * 删除accessToken
     * @param id accessTokenId
     */
    void deleteAccessToken(String id);
}
