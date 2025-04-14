package com.jmal.clouddisk.config;

import com.jmal.clouddisk.interceptor.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.io.File;
import java.util.Locale;

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

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());

		registry.addInterceptor(authInterceptor).addPathPatterns("/**").
				excludePathPatterns("/login/**", "/public/**", "/articles/**", "/error/**", "/file/**" , "/share-file/**", "/files/**","/swagger-ui/**");

        registry.addInterceptor(fileInterceptor).addPathPatterns("/file/**").addPathPatterns("/files/**");

        registry.addInterceptor(shareFileInterceptor).addPathPatterns("/share-file/{shareId}/{token}/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/file/**")
                .addResourceLocations("file:" + fileProperties.getRootDir() + File.separator);
        log.info("静态资源目录:{}", fileProperties.getRootDir() + File.separator);
    }

    public AsyncTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Custom-Executor-");
        executor.initialize();
        return executor;
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
