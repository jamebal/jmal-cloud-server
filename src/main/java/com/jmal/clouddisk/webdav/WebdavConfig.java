package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.webdav.resource.FileResourceSet;
import jakarta.servlet.MultipartConfigElement;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;


@Configuration
public class WebdavConfig {

    private final FileProperties fileProperties;

    private final MyRealm myRealm;

    private final WebdavAuthenticator webdavAuthenticator;

    private final MyWebdavServlet myWebdavServlet;

    public WebdavConfig(FileProperties fileProperties, MyRealm myRealm, WebdavAuthenticator webdavAuthenticator, MyWebdavServlet myWebdavServlet) {
        this.fileProperties = fileProperties;
        this.myRealm = myRealm;
        this.webdavAuthenticator = webdavAuthenticator;
        this.myWebdavServlet = myWebdavServlet;
    }

    /**
     * 自定义 DispatcherServlet 的注册，以确保关键应用程序行为。
     * <ul>
     * <li>确保 DispatcherServlet 处理所有根路径（"/"）请求。</li>
     * <li>通过设置 loadOnStartup = 1 强制立即初始化，解决初始请求延迟问题。</li>
     * <li>注入并设置 MultipartConfigElement，以确保文件上传功能在不同环境下的稳定性，避免自动配置更改带来的影响。</li>
     * </ul>
     * @param dispatcherServlet Spring Boot 自动配置的 DispatcherServlet 实例。
     * @param multipartConfig Spring Boot 根据 multipart 属性自动创建的 Multipart 配置。
     * @return DispatcherServlet 的注册 Bean。
     */
    @Bean
    public ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistration( DispatcherServlet dispatcherServlet, MultipartConfigElement multipartConfig) {
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
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            // 创建一个新的WebResourceRoot实例
            MyStandardRoot standardRoot = new MyStandardRoot(context);
            // 自定义静态资源的位置
            standardRoot.addPreResources(new FileResourceSet(standardRoot, fileProperties.getRootDir()));
            // 将新的WebResourceRoot设置为应用程序的资源根目录
            context.setResources(standardRoot);
            context.getPipeline().addValve(webdavAuthenticator);

            context.setRealm(myRealm);

            // 设置安全约束
            SecurityCollection securityCollection = new SecurityCollection();
            securityCollection.addPattern(fileProperties.getWebDavPrefixPath() + "/*");

            SecurityConstraint securityConstraint = new SecurityConstraint();
            securityConstraint.addAuthRole("webdav");
            securityConstraint.addCollection(securityCollection);

            context.addConstraint(securityConstraint);

            // 设置登录配置
            LoginConfig loginConfig = new LoginConfig();
            loginConfig.setAuthMethod("BASIC");
            context.setLoginConfig(loginConfig);

        });
    }

}
