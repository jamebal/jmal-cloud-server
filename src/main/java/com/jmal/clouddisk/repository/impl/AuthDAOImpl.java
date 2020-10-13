package com.jmal.clouddisk.repository.impl;

import com.jmal.clouddisk.model.UserToken;
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

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public DataSource getDataSource() {
        return DataSource.mongodb;
    }

    @Override
    public UserToken findOneUserToken(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(username));
        return mongoTemplate.findOne(query, UserToken.class, USER_TOKEN_COLLECTION_NAME);
    }

    @Override
    public void updateToken(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(username));
        Update update = new Update();
        update.set("username", username);
        update.set("timestamp", System.currentTimeMillis());
        mongoTemplate.upsert(query, update, USER_TOKEN_COLLECTION_NAME);
    }
}
