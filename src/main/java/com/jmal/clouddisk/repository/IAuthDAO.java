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
     * @return
     */
    UserToken findOneUserToken(String username);

    /***
     * 更新用户token的时间戳
     * @param username 用户名
     */
    void updateToken(String username);
}
