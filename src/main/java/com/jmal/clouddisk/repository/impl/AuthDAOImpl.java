package com.jmal.clouddisk.repository.impl;

import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.UserTokenDO;
import com.jmal.clouddisk.repository.DataSource;
import com.jmal.clouddisk.repository.IAuthDAO;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

/**
 * @Description 用户认证
 * @author jmal
 * @Date 2020/10/13 10:49 上午
 */
@Repository
public class AuthDAOImpl implements IAuthDAO {

    private static final String USER_TOKEN_COLLECTION_NAME = "user_token";

    private static final String USERNAME = "username";

    private static final String ACCESS_TOKEN = "accessToken";

    private static final String ACCESS_TOKEN_COLLECTION_NAME = "access_token";

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public DataSource getDataSource() {
        return DataSource.mongodb;
    }

    @Override
    public UserTokenDO findOneUserToken(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        return mongoTemplate.findOne(query, UserTokenDO.class, USER_TOKEN_COLLECTION_NAME);
    }

    @Override
    public void updateToken(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        Update update = new Update();
        update.set(USERNAME, username);
        update.set("timestamp", System.currentTimeMillis());
        mongoTemplate.upsert(query, update, USER_TOKEN_COLLECTION_NAME);
    }

    @Override
    public UserAccessTokenDO getUserNameByAccessToken(String accessToken) {
        Query query = new Query();
        query.addCriteria(Criteria.where(ACCESS_TOKEN).is(accessToken));
        return mongoTemplate.findOne(query, UserAccessTokenDO.class, ACCESS_TOKEN_COLLECTION_NAME);
    }

    @Override
    public void upsertAccessToken(String username, String accessToken) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        Update update = new Update();
        update.set(USERNAME, username);
        update.set(ACCESS_TOKEN, accessToken);
        mongoTemplate.upsert(query, update, ACCESS_TOKEN_COLLECTION_NAME);
    }
}
