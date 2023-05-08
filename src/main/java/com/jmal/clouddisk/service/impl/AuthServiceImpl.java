package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.LdapConfigDO;
import com.jmal.clouddisk.model.LdapConfigDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.PasswordHash;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jmal
 * @Description AuthServiceImpl
 * @Date 2020-01-25 18:52
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final MongoTemplate mongoTemplate;

    private final LdapOperations ldapTemplate;

    private final UserLoginHolder userLoginHolder;

    private final IUserService userService;

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
        LdapQuery query = LdapQueryBuilder.query()
                .where("uid").is(username);
        ldapTemplate.authenticate(query, password);
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
    public ResponseResult<Object> validOldPass(String userId, String password) {
        ConsumerDO user = mongoTemplate.findById(userId, ConsumerDO.class, UserServiceImpl.COLLECTION_NAME);
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

    @Override
    public ResponseResult<Object> ldapConfig(LdapConfigDTO ldapConfigDTO) {
        // 判断操作用户是否为网盘创建者
        String userId = userLoginHolder.getUserId();
        ConsumerDO consumerDO = userService.userInfoById(userId);
        if (BooleanUtil.isFalse(consumerDO.getCreator())) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
        String id = "6458f8c5bb943e3cf1db5f29";
        LdapConfigDO ldapConfigDO = new LdapConfigDO();
        BeanUtils.copyProperties(ldapConfigDTO, ldapConfigDO);
        ldapConfigDO.setId(id);
        ldapConfigDO.setUserId(userId);
        ldapConfigDO.setPassword(UserServiceImpl.getEncryptPwd(ldapConfigDTO.getPassword(), consumerDO.getPassword()));
        mongoTemplate.save(ldapConfigDTO);
        return ResultUtil.success();
    }

}
