package com.jmal.clouddisk.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author jmal
 * @Description 路径过滤器
 * @Date 2020/10/20 4:12 下午
 */
@Component
@Lazy
public class WebFilter implements Filter {

    public static final String API = "/api";
    public static final Pattern COMPILE = Pattern.compile(API);

    // 后端路径前缀集合
    private static final Set<String> BACKEND_PREFIXES = new HashSet<>(Arrays.asList(
            API, "/webDAV", "/articles", "/blog", "/swagger-ui", "/error"
    ));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();

        if (isBackendPath(path) || isStaticResource(path) || "/index.html".equals(path)) {
            chain.doFilter(request, response);
        } else {
            // 前端路由兜底
            request.getRequestDispatcher("/index.html").forward(request, response);
        }
    }

    /**
     * 判断是否为后端API或特殊后端路径
     */
    private boolean isBackendPath(String path) {
        for (String prefix : BACKEND_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为静态资源
     * 可根据实际情况扩展更多静态资源类型
     */
    private boolean isStaticResource(String path) {
        // 只要包含.认为是静态资源，可根据需要加白名单
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        return lastDot > lastSlash;
    }

}
