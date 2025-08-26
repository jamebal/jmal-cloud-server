package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;

import java.util.List;

/**
 * IAuthDAO
 * @author jmal
 */
public interface IAccessTokenDAO {

    String USERNAME = "username";

    String ACCESS_TOKEN = "accessToken";

    String ACCESS_TOKEN_COLLECTION_NAME = "access_token";

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
     * @param userList userList
     */
    void deleteAllByUser(List<ConsumerDO> userList);

    /***
     * accessToken列表
     * @param username 用户名
     * @return List<UserAccessTokenDTO>
     */
    List<UserAccessTokenDTO> accessTokenList(String username);

    /**
     * 更新accessToken最近访问时间
     * @param username 用户名
     * @param token token
     */
    void updateAccessToken(String username, String token);

    /***
     * 删除accessToken
     * @param id accessTokenId
     */
    void deleteAccessToken(String id);
}
