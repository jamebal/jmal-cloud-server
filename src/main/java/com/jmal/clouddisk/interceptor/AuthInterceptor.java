package com.jmal.clouddisk.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson2.JSON;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.rbac.UserLoginContext;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TokenUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;
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

    public static final String NAME_HEADER = "name";

    public static final String ACCESS_TOKEN = "access-token";

    public static final String REFRESH_TOKEN = "refresh-token";

    private final IAuthDAO authDAO;

    private final UserServiceImpl userService;

    public AuthInterceptor(IAuthDAO authDAO, UserServiceImpl userService) {
        this.authDAO = authDAO;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        // 身份认证
        String username = getUserNameByHeader(request, response);
        if (!CharSequenceUtil.isBlank(username)) {
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
    public String getUserNameByHeader(HttpServletRequest request, HttpServletResponse response) {
        String name = request.getHeader(NAME_HEADER);
        String jmalToken = request.getHeader(JMAL_TOKEN);
        if (CharSequenceUtil.isBlank(jmalToken)) {
            jmalToken = request.getParameter(JMAL_TOKEN);
        }
        if (CharSequenceUtil.isBlank(name)) {
            name = request.getParameter(NAME_HEADER);
        }
        if (CharSequenceUtil.isBlank(name)) {
            name = request.getParameter(IUserService.USERNAME);
        }
        if (CharSequenceUtil.isBlank(jmalToken)) {
            jmalToken = getCookie(request, JMAL_TOKEN);
        }
        if (CharSequenceUtil.isBlank(name)) {
            name = getCookie(request, IUserService.USERNAME);
        }
        if (CharSequenceUtil.isBlank(jmalToken)) {
            return getUserNameByAccessToken(request);
        }
        return getUserNameByJmalToken(request, response, jmalToken, name);
    }

    /***
     * jmal-token 身份认证通过, 设置该身份的权限
     * @param username username
     */
    public void setAuthorities(String username) {
        List<String> authorities = CaffeineUtil.getAuthoritiesCache(username);
        if (authorities == null || authorities.isEmpty()) {
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
        if (requestAttributes != null) {
            String userId = CaffeineUtil.getUserIdCache(username);
            if (CharSequenceUtil.isBlank(userId)) {
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
    public String getUserNameByAccessToken(HttpServletRequest request) {
        String token = request.getHeader(ACCESS_TOKEN);
        if (CharSequenceUtil.isBlank(token)) {
            token = request.getParameter(ACCESS_TOKEN);
        }
        if (CharSequenceUtil.isBlank(token)) {
            return null;
        }
        UserAccessTokenDO userAccessTokenDO = authDAO.getUserNameByAccessToken(token);
        if (userAccessTokenDO == null) {
            return null;
        }
        String username = userAccessTokenDO.getUsername();
        if (CharSequenceUtil.isBlank(username)) {
            return null;
        }
        // access-token 认证通过 设置该身份的权限
        Completable.fromAction(() -> authDAO.updateAccessToken(username, userAccessTokenDO.getAccessToken()))
                .subscribeOn(Schedulers.io())
                .subscribe();
        setAuthorities(username);
        return userAccessTokenDO.getUsername();
    }

    /**
     * 根据jmal-token获取用户名
     *
     * @param request   HttpServletRequest
     * @param response  HttpServletResponse
     * @param jmalToken jmal-token
     * @param name      username
     * @return username
     */
    public String getUserNameByJmalToken(HttpServletRequest request, HttpServletResponse response, String jmalToken, String name) {
        if (!CharSequenceUtil.isBlank(jmalToken) && !CharSequenceUtil.isBlank(name)) {
            String hashPassword = userService.getHashPasswordUserName(name);
            if (hashPassword == null) {
                return null;
            }
            String username = TokenUtil.getTokenKey(jmalToken, hashPassword);
            if (username == null && request != null) {
                String refreshToken = getCookie(request, REFRESH_TOKEN);
                if (CharSequenceUtil.isBlank(refreshToken)) {
                    return null;
                }
                // 自动续token
                username = renewJmalToken(request, response, name, hashPassword, refreshToken);
            }
            if (name.equals(username)) {
                return username;
            }
            return null;
        }
        return null;
    }

    /**
     * 自动续token
     */
    private String renewJmalToken(HttpServletRequest request, HttpServletResponse response, String name, String hashPassword, String refreshToken) {
        String username = TokenUtil.getTokenKey(refreshToken, hashPassword);
        if (name.equals(username)) {
            boolean rememberMe = name.equals(getCookie(request, "rememberName"));
            String jmalToken = generateJmalToken(hashPassword, username);

            Cookie tokenCoolie = new Cookie(JMAL_TOKEN, jmalToken);
            tokenCoolie.setMaxAge(7200);
            tokenCoolie.setHttpOnly(true);
            tokenCoolie.setPath("/");
            response.addCookie(tokenCoolie);
            response.addHeader(JMAL_TOKEN, jmalToken);

            setRefreshCookie(response, hashPassword, username, rememberMe);
        }
        return username;
    }

    /**
     * 生成jmal-token
     *
     * @param hashPassword hashPassword
     * @param username     username
     * @return jmal-token
     */
    public static String generateJmalToken(String hashPassword, String username) {
        LocalDateTime jmalTokenExpiration = LocalDateTime.now();
        // jmal-token 期限为2小时
        jmalTokenExpiration = jmalTokenExpiration.plusSeconds(7200);
        return TokenUtil.createToken(username, hashPassword, jmalTokenExpiration);
    }

    /**
     * 设置 refreshToken
     *
     * @param response     HttpServletResponse
     * @param hashPassword hashPassword
     * @param username     username
     * @param rememberMe   rememberMe
     */
    public static void setRefreshCookie(HttpServletResponse response, String hashPassword, String username, boolean rememberMe) {
        LocalDateTime refreshTokenExpiration = LocalDateTime.now();
        // 如果用户勾选了记住我, refreshToken期限为30天, 否则为1天
        int refreshMaxAge = rememberMe ? 2592000 : 86400;
        refreshTokenExpiration = refreshTokenExpiration.plusSeconds(refreshMaxAge);
        Cookie refreshCookie = new Cookie(REFRESH_TOKEN, TokenUtil.createToken(username, hashPassword, refreshTokenExpiration));
        refreshCookie.setMaxAge(refreshMaxAge);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        response.addCookie(refreshCookie);
    }

    public static void removeAllCookies(HttpServletResponse response) {
        removeCookies(response, AuthInterceptor.REFRESH_TOKEN, AuthInterceptor.JMAL_TOKEN);
    }

    private static void removeCookies(HttpServletResponse response, String ...keys) {
        for (String key : keys) {
            Cookie cookie = new Cookie(key, null);
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);
        }
    }

    public static String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void returnJson(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        ServletOutputStream out = null;
        try {
            out = response.getOutputStream();
            ResponseResult<Object> result = ResultUtil.error(ExceptionType.LOGIN_EXCEPTION.getCode(), ExceptionType.LOGIN_EXCEPTION.getMsg());
            out.write(JSON.toJSONString(result).getBytes());
            AuthInterceptor.removeAllCookies(response);
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
