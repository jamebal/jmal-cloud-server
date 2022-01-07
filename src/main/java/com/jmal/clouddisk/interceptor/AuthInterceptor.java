package com.jmal.clouddisk.interceptor;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.UserTokenDO;
import com.jmal.clouddisk.model.rbac.UserLoginContext;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * @author jmal
 * @Description 鉴权拦截器
 * @Date 2020-01-31 22:04
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String JMAL_TOKEN = "jmal-token";

    public static final String ACCESS_TOKEN = "access-token";

    private static final long ONE_DAY = 1000L * 60 * 60 * 24;
    private final Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    private IAuthDAO authDAO;

    @Autowired
    private UserServiceImpl userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 身份认证
        String username = getUserNameByHeader(request);
        if (!StrUtil.isBlank(username)) {
            // jmal-token 身份认证通过, 设置该身份的权限
            setAuthorities(username);
            return true;
        }
        returnJson(response);
        return false;
    }

    /***
     * 根据header获取用户名
     * @param request request
     * @return 用户名
     */
    public String getUserNameByHeader(HttpServletRequest request){
        String username;
        String jmalToken = request.getHeader(JMAL_TOKEN);
        if (StrUtil.isBlank(jmalToken)) {
            jmalToken = request.getParameter(JMAL_TOKEN);
        }
        if(StrUtil.isBlank(jmalToken)){
            return getUserNameByAccessToken(request);
        }
        username = getUserNameByJmalToken(jmalToken);
        return username;
    }

    /***
     * jmal-token 身份认证通过, 设置该身份的权限
     * @param username username
     */
    private void setAuthorities(String username) {
        List<String> authorities = CaffeineUtil.getAuthoritiesCache(username);
        if(authorities == null || authorities.isEmpty()){
            authorities = userService.getAuthorities(username);
            CaffeineUtil.setAuthoritiesCache(username, authorities);
        }
        setAuthorities(username, authorities);
    }

    /***
     * 设置用户登录信息
     * access-token, jmal-token 通用
     * @param username username
     * @param authorities 权限标示列表
     */
    private void setAuthorities(String username, List<String> authorities) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null){
            String userId = CaffeineUtil.getUserIdCache(username);
            if(StrUtil.isBlank(userId)) {
                userId = userService.getUserIdByUserName(username);
                CaffeineUtil.setUserIdCache(username, userId);
            }
            UserLoginContext userLoginContext = new UserLoginContext();
            userLoginContext.setAuthorities(authorities);
            userLoginContext.setUserId(userId);
            userLoginContext.setUsername(username);
            requestAttributes.setAttribute("user", userLoginContext, 0);
        }
    }

    /***
     * 根据access-token获取用户名
     * @param request request
     * @return 用户名
     */
    public String getUserNameByAccessToken(HttpServletRequest request){
        String token = request.getHeader(ACCESS_TOKEN);
        if (StrUtil.isBlank(token)) {
            token = request.getParameter(ACCESS_TOKEN);
        }
        if(StrUtil.isBlank(token)){
            return null;
        }
        UserAccessTokenDO userAccessTokenDO = authDAO.getUserNameByAccessToken(token);
        if(userAccessTokenDO == null){
            return null;
        }
        String username = userAccessTokenDO.getUsername();
        if(StrUtil.isBlank(username)){
            return null;
        }
        // access-token 认证通过 设置该身份的权限
        ThreadUtil.execute(() -> authDAO.updateAccessToken(username));
        setAuthorities(username);
        return userAccessTokenDO.getUsername();
    }

    /***
     * 根据jmal-token获取用户名
     * @param jmalToken jmalToken
     * @return 用户名
     */
    public String getUserNameByJmalToken(String jmalToken) {
        if (!StrUtil.isBlank(jmalToken)) {
            String username = tokenCache.getIfPresent(jmalToken);
            if (StrUtil.isBlank(username)) {
                String userName = TokenUtil.getUserName(jmalToken);
                if(StrUtil.isBlank(userName)){
                    return null;
                }
                UserTokenDO userTokenDO = authDAO.findOneUserToken(userName);
                if (userTokenDO == null) {
                    return null;
                }
                if ((System.currentTimeMillis() - userTokenDO.getTimestamp()) < ONE_DAY) {
                    ThreadUtil.execute(() -> updateToken(jmalToken, userName));
                    return userTokenDO.getUsername();
                } else {
                    // 登录超时清除用户缓存
                    CaffeineUtil.removeAuthoritiesCache(userName);
                    CaffeineUtil.removeUserIdCache(userName);
                }
                return null;
            } else {
                ThreadUtil.execute(() -> updateToken(jmalToken, username));
                return username;
            }
        }
        return null;
    }



    /**
     * 刷新token
     *
     * @param jmalToken jmalToken
     * @param username username
     */
    private void updateToken(String jmalToken, String username) {
        tokenCache.put(jmalToken, username);
        authDAO.updateToken(username);
    }

    private void returnJson(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        ServletOutputStream out = null;
        try {
            out = response.getOutputStream();
            ResponseResult<Object> result = ResultUtil.error(ExceptionType.LOGIN_EXCEPRION.getCode(), ExceptionType.LOGIN_EXCEPRION.getMsg());
            out.write(JSON.toJSONString(result).getBytes());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            try {
                if (out != null) {
                    out.flush();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
