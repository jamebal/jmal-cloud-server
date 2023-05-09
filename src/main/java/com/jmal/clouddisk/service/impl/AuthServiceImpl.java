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
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
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

    private LdapTemplate ldapTemplate;

    private Boolean ldapEnable;

    private final UserLoginHolder userLoginHolder;

    private final IUserService userService;

    private static final String LOGIN_ERROR = "用户名或密码错误";

    @PostConstruct
    private void init() {
        LdapConfigDO ldapConfigDO = mongoTemplate.findOne(new Query(), LdapConfigDO.class);
        if (ldapConfigDO != null && BooleanUtil.isTrue(ldapConfigDO.getEnable())) {
            ConsumerDO consumerDO = userService.getUserInfoById(ldapConfigDO.getUserId());
            LdapConfigDTO ldapConfigDTO = ldapConfigDO.toLdapConfigDTO(consumerDO);
            LdapContextSource ldapContextSource = loadLdapConfig(ldapConfigDTO);
            ldapTemplate = new LdapTemplate(ldapContextSource);
            ldapEnable = true;
        }
    }

    private static LdapContextSource loadLdapConfig(LdapConfigDTO ldapConfigDTO) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://" + ldapConfigDTO.getLdapServer());
        contextSource.setUserDn(ldapConfigDTO.getBaseDN());
        contextSource.setPassword(ldapConfigDTO.getPassword());
        String[] base = ldapConfigDTO.getBaseDN().split(",");
        if (base.length == 3) {
            contextSource.setBase(base[1] + "," + base[2]);
        }
        return contextSource;
    }

    @Override
    public ResponseResult<Object> login(HttpServletResponse response, ConsumerDTO consumerDTO) {
        String password = consumerDTO.getPassword();
        ConsumerDO consumerDO = userService.getUserInfoByUsername(consumerDTO.getUsername());
        if (consumerDO == null) {
            if (ldapTemplate != null && ldapEnable) {
                // ldap登录
                return ldapLogin(response, consumerDTO);
            }
            return ResultUtil.error(LOGIN_ERROR);
        } else {
            String hashPassword = consumerDO.getPassword();
            if (!CharSequenceUtil.isBlank(password) && PasswordHash.validatePassword(password, hashPassword)) {
                return loginValidSuccess(response, consumerDTO, consumerDO);
            }
        }
        return ResultUtil.error(LOGIN_ERROR);
    }

    private static ResponseResult<Object> loginValidSuccess(HttpServletResponse response, ConsumerDTO userDTO, ConsumerDO consumerDO) {
        Map<String, String> map = new HashMap<>(3);
        String username = userDTO.getUsername();
        String hashPassword = consumerDO.getPassword();
        boolean rememberMe = BooleanUtil.isTrue(userDTO.getRememberMe());
        String jmalToken = AuthInterceptor.generateJmalToken(hashPassword, username, rememberMe);
        map.put("jmalToken", jmalToken);
        map.put("username", username);
        map.put("userId", consumerDO.getId());
        AuthInterceptor.setRefreshCookie(response, hashPassword, username, rememberMe);
        return ResultUtil.success(map);
    }

    private ResponseResult<Object> ldapLogin(HttpServletResponse response, ConsumerDTO consumerDTO) {
        try {
            LdapQuery query = LdapQueryBuilder.query()
                    .where("uid").is(consumerDTO.getUsername());
            ldapTemplate.authenticate(query, consumerDTO.getPassword());
        } catch (Exception e) {
            return ResultUtil.error(LOGIN_ERROR);
        }
        LdapConfigDO ldapConfigDO = mongoTemplate.findOne(new Query(), LdapConfigDO.class);
        if (ldapConfigDO != null) {
            // 创建账号
            consumerDTO.setRoles(ldapConfigDO.getDefaultRoleList());
            ConsumerDO consumerDO = userService.add(consumerDTO);
            return loginValidSuccess(response, consumerDTO, consumerDO);
        }
        return ResultUtil.error(LOGIN_ERROR);
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
    public ResponseResult<Object> updateLdapConfig(LdapConfigDTO ldapConfigDTO) {
        // 判断操作用户是否为网盘创建者
        String userId = userLoginHolder.getUserId();
        ConsumerDO consumerDO = userService.userInfoById(userId);
        if (BooleanUtil.isFalse(consumerDO.getCreator())) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
        LdapConfigDO ldapConfigDO = ldapConfigDTO.toLdapConfigDO(consumerDO);
        mongoTemplate.save(ldapConfigDO);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> testLdapConfig(LdapConfigDTO ldapConfigDTO) {
        LdapContextSource ldapContextSource = loadLdapConfig(ldapConfigDTO);
        LdapTemplate testLdapTemplate = new LdapTemplate(ldapContextSource);
        try {
            // 使用基本查询，以检查LDAP服务器是否可用
            testLdapTemplate.search(
                    LdapQueryBuilder.query().where("objectClass").is("top"),
                    (AttributesMapper<String>) attributes -> attributes.get("objectClass").get().toString());
            return ResultUtil.success();
        } catch (Exception e) {
            return ResultUtil.warning("配置有误");
        }
    }
}


































