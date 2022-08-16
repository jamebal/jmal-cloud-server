package com.jmal.clouddisk.repository.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.model.UserTokenDO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.repository.DataSource;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.util.TimeUntils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    public void generateAccessToken(UserAccessTokenDO userAccessTokenDO) {
        if(CharSequenceUtil.isBlank(userAccessTokenDO.getName())){
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(userAccessTokenDO.getName()));
        if(mongoTemplate.exists(query, ACCESS_TOKEN_COLLECTION_NAME)){
            throw new CommonException(ExceptionType.EXISTING_RESOURCES.getCode(), "该名称已存在");
        }
        userAccessTokenDO.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
        mongoTemplate.save(userAccessTokenDO, ACCESS_TOKEN_COLLECTION_NAME);
    }

    @Override
    public void deleteAllByUser(List<ConsumerDO> userList) {
        if(userList == null || userList.isEmpty()){
            return;
        }
        userList.forEach(user -> {
            String username = user.getUsername();
            Query query = new Query();
            query.addCriteria(Criteria.where(USERNAME).is(username));
            mongoTemplate.remove(query, ACCESS_TOKEN_COLLECTION_NAME);
            mongoTemplate.remove(query, USER_TOKEN_COLLECTION_NAME);
        });
    }

    @Override
    public List<UserAccessTokenDTO> accessTokenList(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        List<UserAccessTokenDO> userAccessTokenDOList = mongoTemplate.find(query, UserAccessTokenDO.class, ACCESS_TOKEN_COLLECTION_NAME);
        return userAccessTokenDOList.stream().map(userAccessTokenDO -> {
            UserAccessTokenDTO userAccessTokenDTO = new UserAccessTokenDTO();
            CglibUtil.copy(userAccessTokenDO, userAccessTokenDTO);
            return userAccessTokenDTO;
        }).collect(Collectors.toList());
    }

    @Override
    public void updateAccessToken(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        Update update = new Update();
        update.set("lastActiveTime", LocalDateTime.now(TimeUntils.ZONE_ID));
        mongoTemplate.upsert(query, update,ACCESS_TOKEN_COLLECTION_NAME);
    }

    @Override
    public void deleteAccessToken(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, ACCESS_TOKEN_COLLECTION_NAME);
    }
}
