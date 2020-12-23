package com.jmal.clouddisk;

import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.model.FileProperties;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * WebConfig
 *
 * @author jmal
 */
@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    FileProperties fileProperties;

    @Autowired
    AuthInterceptor authInterceptor;

    @Autowired
    FileInterceptor fileInterceptor;

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor).addPathPatterns("/**").
				excludePathPatterns("/","/login/**", "/public/**", "/articles/**", "/error/**", "/file/**");
        registry.addInterceptor(fileInterceptor).addPathPatterns("/file/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/file/**").addResourceLocations(
                "file:" + fileProperties.getRootDir() + File.separator).setCacheControl(CacheControl.maxAge(3600, TimeUnit.SECONDS));
        log.info("静态资源目录:{}", fileProperties.getRootDir() + File.separator);
    }
}
