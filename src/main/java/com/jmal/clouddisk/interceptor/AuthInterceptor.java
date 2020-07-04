package com.jmal.clouddisk.interceptor;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Description 鉴权拦截器
 * @Date 2020-01-31 22:04
 * @blame jmal
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String JMAL_TOKEN = "jmal-token";

    private Cache<String,String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    IUserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String jmalToken = request.getHeader(JMAL_TOKEN);
        if(StringUtils.isEmpty(jmalToken)){
            jmalToken = request.getParameter(JMAL_TOKEN);
        }
        if (checkToken(jmalToken)) {
            return true;
        }
        returnJson(response);
        return false;
    }

    public boolean checkToken(String jmalToken) {
        if(!StringUtils.isEmpty(jmalToken)){
            String username = tokenCache.getIfPresent(jmalToken);
            if(!StringUtils.isEmpty(username)){
                return true;
            }
        }
        return false;
    }

    private void returnJson(HttpServletResponse response){
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        ServletOutputStream out = null;
        try {
            out = response.getOutputStream();
            ResponseResult<?> result = ResultUtil.error(ExceptionType.LOGIN_EXCEPRION.getCode(), ExceptionType.LOGIN_EXCEPRION.getMsg());
            out.write(JSON.toJSONString(result).getBytes());
        } catch (IOException e) {
            throw new CommonException(ExceptionType.LOGIN_EXCEPRION.getCode(), ExceptionType.LOGIN_EXCEPRION.getMsg());
        } finally {
            try {
                if (out != null) {
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
