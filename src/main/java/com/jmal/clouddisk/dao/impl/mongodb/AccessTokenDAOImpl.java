package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IAccessTokenDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description 用户认证
 * @author jmal
 * @Date 2020/10/13 10:49 上午
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class AccessTokenDAOImpl implements IAccessTokenDAO {

    private final MongoTemplate mongoTemplate;

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
        });
    }

    @Override
    public List<UserAccessTokenDTO> accessTokenList(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        List<UserAccessTokenDO> userAccessTokenDOList = mongoTemplate.find(query, UserAccessTokenDO.class, ACCESS_TOKEN_COLLECTION_NAME);
        return userAccessTokenDOList.stream().map(userAccessTokenDO -> {
            UserAccessTokenDTO userAccessTokenDTO = new UserAccessTokenDTO();
            BeanUtils.copyProperties(userAccessTokenDO, userAccessTokenDTO);
            return userAccessTokenDTO;
        }).collect(Collectors.toList());
    }

    @Override
    public void updateAccessToken(String username, String token) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        query.addCriteria(Criteria.where(ACCESS_TOKEN).is(token));
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
