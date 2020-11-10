package com.jmal.clouddisk.repository;

import com.jmal.clouddisk.model.UserToken;

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
    UserToken findOneUserToken(String username);

    /***
     * 更新用户token的时间戳
     * @param username 用户名
     */
    void updateToken(String username);

    /***
     * 根据AccessToken获取用户名
     * @param accessToken accessToken
     * @return 用户名
     */
    String getUserNameByAccessToken(String accessToken);

    /***
     * 创建accessToken
     * @param username 用户名
     * @param accessToken accessToken
     */
    void upsertAccessToken(String username, String accessToken);
}
