package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.util.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    @Override
    public ResponseResult<Object> login(HttpServletRequest request, HttpServletResponse response, String userName, String password) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(userName));
        ConsumerDO user = mongoTemplate.findOne(query, ConsumerDO.class, UserServiceImpl.COLLECTION_NAME);
        if (user == null) {
            return ResultUtil.error(LOGIN_ERROR);
        } else {
            String userPassword = user.getPassword();
            if (!CharSequenceUtil.isBlank(password) && PasswordHash.validatePassword(password, userPassword)) {
                Map<String, String> map = new HashMap<>(3);
                boolean rememberMe = !StrUtil.isBlank(request.getHeader("RememberMe"));
                LocalDateTime refreshTokenExpirat = LocalDateTime.now();
                LocalDateTime jmalTokenExpirat = LocalDateTime.now();
                int refreshMaxAge = rememberMe ? 180 * 24 * 3600 : 24 * 3600;
                refreshTokenExpirat = refreshTokenExpirat.plusDays(rememberMe ? 180 : 1);
                jmalTokenExpirat = jmalTokenExpirat.plusSeconds(rememberMe ? 30 * 24 : 10);
                map.put("jmalToken", TokenUtil.createToken(userName, userPassword, jmalTokenExpirat));
                map.put("username", userName);
                map.put("userId", user.getId());
                Cookie cookie = new Cookie(AuthInterceptor.REFRESH_TOKEN, TokenUtil.createToken(userName, userPassword, refreshTokenExpirat));
                cookie.setMaxAge(refreshMaxAge);
                cookie.setHttpOnly(true);
                response.addCookie(cookie);
                return ResultUtil.success(map);
            }
        }
        return ResultUtil.error(LOGIN_ERROR);
    }

    @Override
    public ResponseResult<Object> logout(String token, HttpServletResponse response) {
        Cookie cookie = new Cookie(AuthInterceptor.REFRESH_TOKEN, null);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
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
            if (!CharSequenceUtil.isBlank(password)) {
                if (PasswordHash.validatePassword(password, userPassword)) {
                    return ResultUtil.success();
                }
            }
        }
        return ResultUtil.warning(LOGIN_ERROR);
    }
}
