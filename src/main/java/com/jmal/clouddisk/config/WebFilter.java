package com.jmal.clouddisk.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
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

    private static final String[] BACKEND_PREFIXES = {"/api", "/webDAV", "/articles", "/blog", "/swagger-ui", "/favicon.ico", "/error"};

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();

        // 排除后端接口和静态资源
        if (isBackend(path) || path.contains(".") || "/index.html".equals(path)) {
            chain.doFilter(request, response);
        } else {
            // 兜底到前端
            request.getRequestDispatcher("/index.html").forward(request, response);
        }
    }

    private boolean isBackend(String path) {
        for (String prefix : BACKEND_PREFIXES) {
            if (path.startsWith(prefix + "/") || path.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

}
