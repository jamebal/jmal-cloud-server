package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jmal
 * @Description AuthServiceImpl
 * @Date 2020-01-25 18:52
 */
@Service
public class AuthServiceImpl implements IAuthService {

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    UserLoginHolder userLoginHolder;

    private static final String LOGIN_ERROR = "用户名或密码错误";

    private final Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Override
    public ResponseResult<Object> login(String userName, String password) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(userName));
        ConsumerDO user = mongoTemplate.findOne(query, ConsumerDO.class, UserServiceImpl.COLLECTION_NAME);
        if (user == null) {
            return ResultUtil.error(LOGIN_ERROR);
        } else {
            String userPassword = user.getPassword();
            if (!CharSequenceUtil.isBlank(password) && PasswordHash.validatePassword(password, userPassword)) {
                Map<String, String> map = new HashMap<>(3);
                String token = TokenUtil.createTokens(userName);
                map.put("token", token);
                map.put("username", userName);
                map.put("userId", user.getId());
                tokenCache.put(token, userName);
                return ResultUtil.success(map);
            }
        }
        return ResultUtil.error(LOGIN_ERROR);
    }

    @Override
    public ResponseResult<Object> logout(String token) {
        tokenCache.invalidate(token);
        String username = userLoginHolder.getUsername();
        CaffeineUtil.removeAuthoritiesCache(username);
        CaffeineUtil.removeUserIdCache(username);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> validOldPass(String id, String password) {
        ConsumerDO user = mongoTemplate.findById(id, ConsumerDO.class, UserServiceImpl.COLLECTION_NAME);
        if (user == null) {
            return ResultUtil.warning(LOGIN_ERROR);
        } else {
            String userPassword = user.getPassword();
            if (!CharSequenceUtil.isBlank(password)) {
                if (PasswordHash.validatePassword(password, userPassword)) {
                    return ResultUtil.success();
                }
            }
        }
        return ResultUtil.warning(LOGIN_ERROR);
    }
}
