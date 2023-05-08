package com.jmal.clouddisk.service.impl;

import cn.hutool.core.lang.Console;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.PasswordHash;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ldap.core.LdapTemplate;
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
    LdapTemplate ldapTemplate;

    @Autowired
    UserLoginHolder userLoginHolder;

    private static final String LOGIN_ERROR = "用户名或密码错误";

    @Override
    public ResponseResult<Object> login(HttpServletResponse response, ConsumerDTO userDTO) {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(username));
        ConsumerDO user = mongoTemplate.findOne(query, ConsumerDO.class, UserServiceImpl.COLLECTION_NAME);
        if (user == null) {
            return ResultUtil.error(LOGIN_ERROR);
        } else {
            String hashPassword = user.getPassword();
            if (!CharSequenceUtil.isBlank(password) && PasswordHash.validatePassword(password, hashPassword)) {
                Map<String, String> map = new HashMap<>(3);
                boolean rememberMe = BooleanUtil.isTrue(userDTO.getRememberMe());
                String jmalToken = AuthInterceptor.generateJmalToken(hashPassword, username, rememberMe);
                map.put("jmalToken", jmalToken);
                map.put("username", username);
                map.put("userId", user.getId());
                AuthInterceptor.setRefreshCookie(response, hashPassword, username, rememberMe);
                return ResultUtil.success(map);
            }
        }
        return ResultUtil.error(LOGIN_ERROR);
    }

    @Override
    public ResponseResult<Object> ldapLogin(HttpServletResponse response, String username, String password) {
        boolean auth = ldapTemplate.authenticate("ou=people", "uid=" + username, password);
        Console.log(auth);
        return null;
    }

    @Override
    public ResponseResult<Object> logout(String token, HttpServletResponse response) {
        Cookie cookie = new Cookie(AuthInterceptor.REFRESH_TOKEN, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
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
            if (!CharSequenceUtil.isBlank(password) && PasswordHash.validatePassword(password, userPassword)) {
                return ResultUtil.success();
            }
        }
        return ResultUtil.warning(LOGIN_ERROR);
    }
}
