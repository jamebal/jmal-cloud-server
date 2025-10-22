package com.jmal.clouddisk.config;

import com.jmal.clouddisk.interceptor.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebConfig
 *
 * @author jmal
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final FileProperties fileProperties;

    private final AuthInterceptor authInterceptor;

    private final FileInterceptor fileInterceptor;

    private final ShareFileInterceptor shareFileInterceptor;

    private final DirectFileInterceptor directFileInterceptor;

    private final PreFileInterceptor preFileInterceptor;

    public static final String API_FILE_PREFIX = "/api/file/";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 只给@RestController加/api前缀
        configurer.addPathPrefix("/api", c -> c.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class));
    }

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());

		registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").
				excludePathPatterns("/api", "/api/login/**", "/api/public/**", "/articles/**", "/api/error/**", API_FILE_PREFIX + "**" , "/api/pre-file/**" , "/api/share-file/**", "/api/direct-file/**" , "/api/files/**","/api/swagger-ui/**");

        registry.addInterceptor(fileInterceptor).addPathPatterns(API_FILE_PREFIX + "**").addPathPatterns("/api/files/**");

        registry.addInterceptor(shareFileInterceptor).addPathPatterns("/api/share-file/**");

        registry.addInterceptor(directFileInterceptor).addPathPatterns("/api/direct-file/{mark}/{filename}");

        registry.addInterceptor(preFileInterceptor).addPathPatterns("/api/pre-file/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(API_FILE_PREFIX + "**")
                .addResourceLocations("file:" + fileProperties.getRootDir() + File.separator);

        // 前端资源
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + fileProperties.getFrontendResourcePath());
        // pdfjs
        registry.addResourceHandler("/pdf.js/**")
                .addResourceLocations("file:" + fileProperties.getPdfjsResourcePath());
        // drawio
        registry.addResourceHandler("/drawio/webapp/**")
                .addResourceLocations("file:" + fileProperties.getDrawioResourcePath());
        // excalidraw
        registry.addResourceHandler("/excalidraw/app/**")
                .addResourceLocations("file:" + fileProperties.getExcalidrawResourcePath());

        // 博客静态资源
        registry.addResourceHandler("/articles/**")
                .addResourceLocations("classpath:/static/articles/");

        log.debug("网盘文件根目录:{}", fileProperties.getRootDir() + File.separator);
    }

    public AsyncTaskExecutor taskExecutor() {
        ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return new ConcurrentTaskExecutor(virtualThreadExecutor);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }

    @Override
    public void configureAsyncSupport(@NotNull AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(taskExecutor());
        // configurer.setDefaultTimeout(3000);
        WebMvcConfigurer.super.configureAsyncSupport(configurer);
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("locale");
        resolver.setDefaultLocale(Locale.US);
        return resolver;
    }

    @Bean
    public HeaderLocaleChangeInterceptor localeChangeInterceptor() {
        return new HeaderLocaleChangeInterceptor();
    }

}
