package com.jmal.clouddisk.config;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * @author jmal
 * @Description 错误页
 * @Date 2020/12/16 3:39 下午
 */
@Component
public class ErrorConfig implements ErrorPageRegistrar {
    @Override
    public void registerErrorPages(ErrorPageRegistry registry) {
        ErrorPage[] errorPages = new ErrorPage[1];
        errorPages[0] = new ErrorPage(HttpStatus.NOT_FOUND, "/public/404");
        registry.addErrorPages(errorPages);
    }
}
