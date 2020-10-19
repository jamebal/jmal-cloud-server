package com.jmal.clouddisk;

import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.model.FilePropertie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * WebConfig
 *
 * @author jmal
 */
@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    FilePropertie filePropertie;

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
				excludePathPatterns("/login/**", "/public/**", "/file/**");
        registry.addInterceptor(fileInterceptor).addPathPatterns("/file/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/file/**").addResourceLocations(
                "file:" + filePropertie.getRootDir() + File.separator);
        log.info("静态资源目录:{}",filePropertie.getRootDir() + File.separator);
    }
}
