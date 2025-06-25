package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.webdav.resource.FileResourceSet;
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
     * 手动注册 DispatcherServlet，并强制其在启动时立即初始化。
     * @param dispatcherServlet Spring Boot 自动配置好的 DispatcherServlet 实例
     * @return ServletRegistrationBean
     */
    @Bean
    public ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistration(DispatcherServlet dispatcherServlet) {
        ServletRegistrationBean<DispatcherServlet> registration = new ServletRegistrationBean<>(dispatcherServlet);
        registration.addUrlMappings("/");
        registration.setLoadOnStartup(1);
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
