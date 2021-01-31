package com.jmal.clouddisk.service.impl;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
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
 * @author jmal
 * @Description AuthServiceImpl
 * @Author jmal
 * @Date 2020-01-25 18:52
 */
@Service
public class AuthServiceImpl implements IAuthService {

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    UserLoginHolder userLoginHolder;

    private final Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Override
    public ResponseResult<Object> login(String userName, String password) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(userName));
        ConsumerDO user = mongoTemplate.findOne(query, ConsumerDO.class, UserServiceImpl.COLLECTION_NAME);
        if (user == null) {
            return ResultUtil.error("该用户不存在");
        } else {
            String userPassword = user.getPassword();
            if (!StringUtils.isEmpty(password)) {
                if (SecureUtil.md5(password).equals(userPassword)) {
                    Map<String, String> map = new HashMap<>(3);
                    String token = TokenUtil.createTokens(userName);
                    map.put("token", token);
                    map.put("username", userName);
                    map.put("userId", user.getId());
                    tokenCache.put(token, userName);
                    return ResultUtil.success(map);
                }
            }
        }
        return ResultUtil.error("用户名或密码错误");
    }

    public static void main(String[] args) {
        String content = "wigojmafasdfasdfasdfasdfl";

        String encryptHex = "1c43e028f1c0d7a110fa625b28812b922d42c0e72ef094b38e0fe2993bbf0dd2";
        long stime = System.currentTimeMillis();
        SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, "e10adc3949ba59abbe56e057f20f883e".getBytes());

        Console.log(aes.encryptHex(content));

        String decryptStr = aes.decryptStr(encryptHex, CharsetUtil.CHARSET_UTF_8);

        Console.log(decryptStr, System.currentTimeMillis() - stime);
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
            return ResultUtil.warning("该用户不存在");
        } else {
            String userPassword = user.getPassword();
            if (!StringUtils.isEmpty(password)) {
                if (SecureUtil.md5(password).equals(userPassword)) {
                    return ResultUtil.success();
                }
            }
        }
        return ResultUtil.warning("密码错误");
    }
}
