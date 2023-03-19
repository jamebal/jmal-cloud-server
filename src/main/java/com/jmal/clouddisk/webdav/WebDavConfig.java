package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@EnableWebMvc
@Configuration
public class WebDavConfig {

    @Autowired
    FileProperties fileProperties;

    @Bean
    public ServletRegistrationBean<MyWebDavServlet> webdavServlet() {
        MyWebDavServlet webdavServlet = new MyWebDavServlet();
        ServletRegistrationBean<MyWebDavServlet> registration = new ServletRegistrationBean<>(new MyWebDavServlet(), fileProperties.getWebDavPrefixPath() + "/*");
        registration.setName("WebDAV servlet");
        registration.setServlet(webdavServlet);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("listings", String.valueOf(true));
        registration.addInitParameter("readonly", String.valueOf(false));
        registration.addInitParameter("debug", String.valueOf(1));
        // registration.addInitParameter("level-2-locked-operations", "true");
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

            WebDAVAuthenticator digestAuthenticator = new WebDAVAuthenticator();
            context.getPipeline().addValve(digestAuthenticator);

            // 添加用户和角色
            context.setRealm(new MyRealm());

            // 设置安全约束
            SecurityCollection securityCollection = new SecurityCollection();
            securityCollection.addPattern("/webDAV/*");

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
