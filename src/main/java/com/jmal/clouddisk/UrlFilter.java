package com.jmal.clouddisk;


import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author jmal
 * @Description 路径过滤器
 * @Date 2020/10/20 4:12 下午
 */
@Component
public class UrlFilter implements Filter {

    private static final String API = "/api";
    private static final Pattern COMPILE = Pattern.compile(API);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        String path = httpRequest.getRequestURI();
        if(path.startsWith(API)) {
            path = COMPILE.matcher(path).replaceFirst("");
            httpRequest.getRequestDispatcher(path).forward(request,response);
        } else {
            chain.doFilter(request,response);

        }
    }
}
