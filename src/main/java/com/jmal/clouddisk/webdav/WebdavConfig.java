package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@EnableWebMvc
@Configuration
public class WebdavConfig {

    private final FileProperties fileProperties;

    private final MyRealm myRealm;

    private final WebdavAuthenticator webdavAuthenticator;

    public WebdavConfig(FileProperties fileProperties, MyRealm myRealm, WebdavAuthenticator webdavAuthenticator) {
        this.fileProperties = fileProperties;
        this.myRealm = myRealm;
        this.webdavAuthenticator = webdavAuthenticator;
    }

    @Bean
    public ServletRegistrationBean<WebdavServlet> webdavServlet() {
        ServletRegistrationBean<WebdavServlet> registration = new ServletRegistrationBean<>(new WebdavServlet(), fileProperties.getWebDavPrefixPath() + "/*");
        registration.setName("WebDAV servlet");
        registration.setServlet(new WebdavServlet());
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
            standardRoot.addPreResources(new MyDirResourceSet(standardRoot, "/", fileProperties.getRootDir(), "/"));
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
            loginConfig.setAuthMethod("DIGEST");
            context.setLoginConfig(loginConfig);

        });
    }

}
