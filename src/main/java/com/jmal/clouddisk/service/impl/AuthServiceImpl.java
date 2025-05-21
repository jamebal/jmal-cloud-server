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
import com.jmal.clouddisk.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

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

    private Boolean ldapEnable = false;

    private String ldapLoginName = "uid";

    private final UserLoginHolder userLoginHolder;

    private final IUserService userService;

    private final MessageUtil messageUtil;

    public String loginError() {
        return messageUtil.getMessage("login.error");
    }

    @PostConstruct
    private void init() {
        LdapConfigDO ldapConfigDO = mongoTemplate.findOne(new Query(), LdapConfigDO.class);
        if (ldapConfigDO != null) {
            ConsumerDO consumerDO = userService.getUserInfoById(ldapConfigDO.getUserId());
            LdapConfigDTO ldapConfigDTO = ldapConfigDO.toLdapConfigDTO(consumerDO);
            LdapContextSource ldapContextSource = loadLdapConfig(ldapConfigDTO);
            ldapTemplate = new LdapTemplate(ldapContextSource);
            ldapEnable = ldapConfigDTO.getEnable();
            ldapLoginName = ldapConfigDO.getLoginName();
        }
    }

    private static LdapContextSource loadLdapConfig(LdapConfigDTO ldapConfigDTO) {
        if (isNotValidDn(ldapConfigDTO.getBaseDN())) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "BaseDN格式错误, 应为 dc=xxx,dc=xxx");
        }
        if (isNotValidDn(ldapConfigDTO.getUserDN())) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "账号格式错误, 应为 cn=xxx,ou=xxx,dc=xxx 或者 uid=xxx,ou=xxx,dc=xxx");
        }
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://" + ldapConfigDTO.getLdapServer());
        contextSource.setUserDn(ldapConfigDTO.getUserDN());
        contextSource.setPassword(ldapConfigDTO.getPassword());
        contextSource.setBase(ldapConfigDTO.getBaseDN());
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Override
    public ResponseResult<Object> login(HttpServletResponse response, ConsumerDTO consumerDTO) {
        String password = consumerDTO.getPassword();
        ConsumerDO consumerDO = userService.getUserInfoByUsername(consumerDTO.getUsername());
        if (consumerDO == null) {
            if (ldapTemplate != null && BooleanUtil.isTrue(ldapEnable)) {
                // ldap登录
                return ldapLogin(response, consumerDTO);
            }
            return ResultUtil.error(loginError());
        } else {
            String hashPassword = consumerDO.getPassword();
            if (!CharSequenceUtil.isBlank(password) && PasswordHash.validatePassword(password, hashPassword)) {
                return loginValidSuccess(response, consumerDTO, consumerDO);
            }
        }
        return ResultUtil.error(loginError());
    }

    private static ResponseResult<Object> loginValidSuccess(HttpServletResponse response, ConsumerDTO userDTO, ConsumerDO consumerDO) {
        Map<String, String> map = new HashMap<>(3);
        String username = userDTO.getUsername();
        String hashPassword = consumerDO.getPassword();
        boolean rememberMe = BooleanUtil.isTrue(userDTO.getRememberMe());
        String jmalToken = AuthInterceptor.generateJmalToken(hashPassword, username);
        map.put("jmalToken", jmalToken);
        map.put("username", username);
        map.put("userId", consumerDO.getId());
        AuthInterceptor.setRefreshCookie(response, hashPassword, consumerDO.getId(), username, rememberMe, jmalToken);
        return ResultUtil.success(map);
    }

    private ResponseResult<Object> ldapLogin(HttpServletResponse response, ConsumerDTO consumerDTO) {
        try {
            LdapQuery query = LdapQueryBuilder.query().where(ldapLoginName).is(consumerDTO.getUsername());
            ldapTemplate.authenticate(query, consumerDTO.getPassword());
        } catch (Exception e) {
            return ResultUtil.error(loginError());
        }
        LdapConfigDO ldapConfigDO = mongoTemplate.findOne(new Query(), LdapConfigDO.class);
        if (ldapConfigDO != null) {
            // 创建账号
            consumerDTO.setRoles(ldapConfigDO.getDefaultRoleList());
            consumerDTO.setShowName(consumerDTO.getUsername());
            ConsumerDO consumerDO = userService.add(consumerDTO);
            return loginValidSuccess(response, consumerDTO, consumerDO);
        }
        return ResultUtil.error(loginError());
    }

    /**
     * 验证LDAP DN(Distinguished Name)字符串是否有效
     *
     * @param dn 要验证的DN字符串
     * @return 如果DN无效，则为true；否则为false
     */
    public static boolean isNotValidDn(String dn) {
        if (dn == null || dn.trim().isEmpty()) {
            return true;
        }
        return !VALID_DN_PATTERN.matcher(dn.trim().toLowerCase()).matches();
    }

    // 静态常量定义正则表达式，只允许逗号分隔
    private static final Pattern VALID_DN_PATTERN = Pattern.compile(
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$|^(uid|cn|ou|dc|o|l|st|c)=([^,]*),(uid|cn|ou|dc|o|l|st|c)=([^,]*)((,(uid|cn|ou|dc|o|l|st|c)=([^,]*))*)(,)?$"
    );

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
            return ResultUtil.warning(loginError());
        } else {
            String userPassword = user.getPassword();
            if (!CharSequenceUtil.isBlank(password) && PasswordHash.validatePassword(password, userPassword)) {
                return ResultUtil.success();
            }
        }
        return ResultUtil.warning(loginError());
    }

    @Override
    public ResponseResult<Object> updateLdapConfig(LdapConfigDTO ldapConfigDTO) {
        // 先测试连接
        this.ldapTemplate = testLdapConfig(ldapConfigDTO);
        // 判断操作用户是否为网盘创建者
        String userId = userLoginHolder.getUserId();
        ConsumerDO consumerDO = userService.userInfoById(userId);
        if (BooleanUtil.isFalse(consumerDO.getCreator())) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
        LdapConfigDO ldapConfigDO = ldapConfigDTO.toLdapConfigDO(consumerDO);
        mongoTemplate.save(ldapConfigDO);
        // 重新加载ldap配置
        init();
        return ResultUtil.success();
    }

    @Override
    public LdapTemplate testLdapConfig(LdapConfigDTO ldapConfigDTO) {
        LdapContextSource ldapContextSource = loadLdapConfig(ldapConfigDTO);
        LdapTemplate testLdapTemplate = new LdapTemplate(ldapContextSource);
        try {
            // 使用基本查询，以检查LDAP服务器是否可用
            testLdapTemplate.search(
                    LdapQueryBuilder.query().where("objectClass").is("*"),
                    (AttributesMapper<String>) attributes -> attributes.get("objectClass").get().toString());
            return testLdapTemplate;
        } catch (CommunicationException e) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "地址错误: 请检查LDAP服务器地址");
        } catch (AuthenticationException e) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "认证失败: 请检查 Base DN 或 密码");
        } catch (Exception e) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "配置有误: " + e.getMessage());
        }
    }

    @Override
    public LdapConfigDTO loadLdapConfig() {
        LdapConfigDO ldapConfigDO = mongoTemplate.findOne(new Query(), LdapConfigDO.class);
        if (ldapConfigDO == null) {
            return null;
        }
        return ldapConfigDO.toLdapConfigDTO();
    }
}


































