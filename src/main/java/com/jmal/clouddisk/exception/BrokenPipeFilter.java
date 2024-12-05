package com.jmal.clouddisk.exception;

import jakarta.servlet.*;

import java.io.IOException;

public class BrokenPipeFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (IOException e) {
            if (e.getMessage().contains("Broken pipe")) {
                // 忽略 Broken pipe 错误
                return;
            }
            throw e;
        }
    }

    @Override
    public void destroy() {}
}
