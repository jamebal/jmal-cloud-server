package com.jmal.clouddisk.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.model.Consumer;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description AuthServiceImpl
 * @Author jmal
 * @Date 2020-01-25 18:52
 * @blame jmal
 */
@Service
public class AuthServiceImpl implements IAuthService {

    @Autowired
    MongoTemplate mongoTemplate;

    private Cache<String,String> tokenCache = CaffeineUtil.getTokenCache();

    @Override
    public ResponseResult<Object> login(String userName, String password) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(userName));
        Consumer user = mongoTemplate.findOne(query, Consumer.class, UserServiceImpl.COLLECTION_NAME);
        if(user == null){
            return ResultUtil.error("该用户不存在");
        }else{
            String userPassword = user.getPassword();
            if(!StringUtils.isEmpty(password)){
                if(SecureUtil.md5(password).equals(userPassword)){
                    Map<String,String> map = new HashMap<>(3);
                    String token = TokenUtil.createTokens(userName);
                    map.put("token",TokenUtil.createTokens(userName));
                    map.put("username",userName);
                    map.put("userId",user.getId());
                    tokenCache.put(token,userName);
                    return ResultUtil.success(map);
                }
            }
        }
        return ResultUtil.error("用户名或密码错误");
    }

    @Override
    public ResponseResult<Object> logout(String token) {
        tokenCache.invalidate(token);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> authentication(String userName, String token) {
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> validOldPass(String id, String password) {
        Consumer user = mongoTemplate.findById(id, Consumer.class, UserServiceImpl.COLLECTION_NAME);
        if(user == null){
            return ResultUtil.warning("该用户不存在");
        }else{
            String userPassword = user.getPassword();
            if(!StringUtils.isEmpty(password)){
                if(SecureUtil.md5(password).equals(userPassword)){
                    return ResultUtil.success();
                }
            }
        }
        return ResultUtil.warning("密码错误");
    }
}
