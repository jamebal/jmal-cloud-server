package com.jmal.clouddisk.interceptor;

import cn.hutool.core.lang.Console;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UserToken;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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

    private static final long ONE_WEEK = 7 * 1000L * 60 * 60 * 24;

    private final Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    IAuthDAO authDAO;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!StringUtils.isEmpty(getUserNameByHeader(request))) {
            return true;
        }
        returnJson(response);
        return false;
    }

    /***
     * 根据jmal-token获取用户名
     * @param request request
     * @return 用户名
     */
    public String getUserNameByHeader(HttpServletRequest request){
        String jmalToken = request.getHeader(JMAL_TOKEN);
        if (StringUtils.isEmpty(jmalToken)) {
            jmalToken = request.getParameter(JMAL_TOKEN);
        }
        return getUserNameByToken(jmalToken);
    }

    /***
     * 根据access-token获取用户名
     * @param request request
     * @return 用户名
     */
    public String getUserNameByAccessToken(HttpServletRequest request){
        String token = request.getHeader(ACCESS_TOKEN);
        if (StringUtils.isEmpty(token)) {
            token = request.getParameter(ACCESS_TOKEN);
        }
        String username = authDAO.getUserNameByAccessToken(token);
        if(username == null){
            throw new CommonException(ExceptionType.ACCESS_FORBIDDEN.getCode(), ExceptionType.ACCESS_FORBIDDEN.getMsg());
        }
        return username;
    }

    /***
     * 根据jmal-token获取用户名
     * @param jmalToken jmalToken
     * @return 用户名
     */
    public String getUserNameByToken(String jmalToken) {
        if (!StringUtils.isEmpty(jmalToken)) {
            String username = tokenCache.getIfPresent(jmalToken);
            if (StringUtils.isEmpty(username)) {
                String userName = TokenUtil.getUserName(jmalToken);
                if(StringUtils.isEmpty(userName)){
                    return null;
                }
                UserToken userToken = authDAO.findOneUserToken(userName);
                if (userToken == null) {
                    return null;
                }
                if ((System.currentTimeMillis() - userToken.getTimestamp()) < ONE_WEEK) {
                    ThreadUtil.execute(() -> updateToken(jmalToken, userName));
                    return userToken.getUsername();
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
            ResponseResult result = ResultUtil.error(ExceptionType.LOGIN_EXCEPRION.getCode(), ExceptionType.LOGIN_EXCEPRION.getMsg());
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
