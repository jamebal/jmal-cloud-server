package com.jmal.clouddisk.interceptor;

import cn.hutool.core.thread.ThreadUtil;
import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
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

/**
 * @author jmal
 * @Description 鉴权拦截器
 * @Date 2020-01-31 22:04
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String JMAL_TOKEN = "jmal-token";

    private static final long ONE_WEEK = 7 * 1000L * 60 * 60 * 24;

    private final Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    IAuthDAO authDAO;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String jmalToken = request.getHeader(JMAL_TOKEN);
        if (StringUtils.isEmpty(jmalToken)) {
            jmalToken = request.getParameter(JMAL_TOKEN);
        }
        if (checkToken(jmalToken)) {
            return true;
        }
        returnJson(response);
        return false;
    }

    public boolean checkToken(String jmalToken) {
        if (!StringUtils.isEmpty(jmalToken)) {
            String username = tokenCache.getIfPresent(jmalToken);
            if (StringUtils.isEmpty(username)) {
                String userName = TokenUtil.getUserName(jmalToken);
                UserToken userToken = authDAO.findOneUserToken(userName);
                if (userToken == null) {
                    return false;
                }
                if ((System.currentTimeMillis() - userToken.getTimestamp()) < ONE_WEEK) {
                    ThreadUtil.execute(() -> updateToken(jmalToken, userName));
                    return true;
                }
                return false;
            } else {
                ThreadUtil.execute(() -> updateToken(jmalToken, username));
                return true;
            }
        }
        return false;
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
