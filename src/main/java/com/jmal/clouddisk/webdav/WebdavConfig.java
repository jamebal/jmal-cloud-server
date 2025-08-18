package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import jakarta.servlet.MultipartConfigElement;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;


@Configuration
@RequiredArgsConstructor
public class WebdavConfig {

    private final FileProperties fileProperties;

    private final MyWebdavServlet myWebdavServlet;

    /**
     * 自定义 DispatcherServlet 的注册，以确保关键应用程序行为。
     * <ul>
     * <li>确保 DispatcherServlet 处理所有根路径（"/"）请求。</li>
     * <li>通过设置 loadOnStartup = 1 强制立即初始化，解决初始请求延迟问题。</li>
     * <li>注入并设置 MultipartConfigElement，以确保文件上传功能在不同环境下的稳定性，避免自动配置更改带来的影响。</li>
     * </ul>
     *
     * @param dispatcherServlet Spring Boot 自动配置的 DispatcherServlet 实例。
     * @param multipartConfig   Spring Boot 根据 multipart 属性自动创建的 Multipart 配置。
     * @return DispatcherServlet 的注册 Bean。
     */
    @Bean
    public ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistration(DispatcherServlet dispatcherServlet, MultipartConfigElement multipartConfig) {
        ServletRegistrationBean<DispatcherServlet> registration = new ServletRegistrationBean<>(dispatcherServlet);
        // 1. 确保处理根路径
        registration.addUrlMappings("/");
        // 2. 确保立即启动，解决第一个请求慢的问题
        registration.setLoadOnStartup(1);
        // 3. 确保文件上传功能正常，抵御自动配置变化
        registration.setMultipartConfig(multipartConfig);
        return registration;
    }

    @Bean
    public DispatcherServletPath dispatcherServletPath() {
        return () -> "/";
    }

    @Bean
    public ServletRegistrationBean<MyWebdavServlet> webdavServlet() {
        ServletRegistrationBean<MyWebdavServlet> registration = new ServletRegistrationBean<>(myWebdavServlet, fileProperties.getWebDavPrefixPath() + "/*");
        registration.setName("WebDAV servlet");
        registration.setServlet(myWebdavServlet);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("listings", String.valueOf(true));
        registration.addInitParameter("readonly", String.valueOf(false));
        registration.addInitParameter("debug", String.valueOf(0));
        return registration;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webServerFactoryCustomizer(MyTomcatContextCustomizer myTomcatContextCustomizer) {
        return factory -> factory.addContextCustomizers(myTomcatContextCustomizer);
    }

}

