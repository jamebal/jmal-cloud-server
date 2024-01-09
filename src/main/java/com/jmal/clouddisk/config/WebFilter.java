package com.jmal.clouddisk.config;

import com.jmal.clouddisk.util.CaffeineUtil;
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

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();
        if (uri.startsWith(API)) {
            uri = COMPILE.matcher(uri).replaceFirst("");
            httpRequest.getRequestDispatcher(uri).forward(request, response);
            return;
        }
        CaffeineUtil.setLastAccessTimeCache();
        chain.doFilter(request, response);
    }

}
