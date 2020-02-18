package com.jmal.clouddisk.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.common.exception.ExceptionType;
import com.jmal.clouddisk.model.User;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @Description UserServiceImpl
 * @blame jmal
 */
@Service
public class UserServiceImpl implements IUserService {

    static final String COLLECTION_NAME = "user";

    private Cache<String,String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    MongoTemplate mongoTemplate;

    @Override
    public ResponseResult<Object> add(User user) {
        User user1 = getUserInfoByName(user.getUsername());
        if(user1 == null){
            mongoTemplate.save(user, COLLECTION_NAME);
        }
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> delete(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> update(User user) {
        Query query = new Query();
        String userId = user.getId();
        if(!StringUtils.isEmpty(userId)){
            query.addCriteria(Criteria.where("_id").is(userId));
        }else{
            String name = user.getUsername();
            if(!StringUtils.isEmpty(name)){
                query.addCriteria(Criteria.where("username").is(name));
            }else{
                return ResultUtil.success();
            }
        }
        Update update = new Update();
        update.set("username", user.getUsername());
        update.set("showName", user.getShowName());
        update.set("password", user.getPassword());
        update.set("quota", user.getQuota());
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> userInfo(String token) {

        String username = tokenCache.getIfPresent(token);
        if(StringUtils.isEmpty(username)){
            return ResultUtil.success(null);
        }
        Query query = new Query();
        if(StringUtils.isEmpty(username)){
            ResultUtil.error("verification failed");
        }
        query.addCriteria(Criteria.where("username").is(username));
        User user = mongoTemplate.findOne(query,User.class,COLLECTION_NAME);
        return ResultUtil.success(user);
    }

    @Override
    public ResponseResult<Object> userList() {
        Query query = new Query();
        List<User> userList = mongoTemplate.find(query,User.class,COLLECTION_NAME);
        return ResultUtil.success(userList);
    }

    @Override
    public String getUserName(String token) {
        if(StringUtils.isEmpty(token)){
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(),ExceptionType.PERMISSION_DENIED.getMsg());
        }
        String username = tokenCache.getIfPresent(token);
        if(StringUtils.isEmpty(username)){
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(),ExceptionType.PERMISSION_DENIED.getMsg());
        }
        return username;
    }

    private User getUserInfoByName(String name){
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(name));
        return mongoTemplate.findOne(query,User.class,COLLECTION_NAME);
    }

}
