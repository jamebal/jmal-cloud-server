package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.BucketInfo;
import com.jmal.clouddisk.oss.IOssStorageService;
import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.webdav.resource.FileResourceSet;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class WebdavConfig {

    private final FileProperties fileProperties;

    private final MyRealm myRealm;

    private final WebdavAuthenticator webdavAuthenticator;

    private final MyWebdavServlet myWebdavServlet;

    private static ApplicationContext context;

    private final List<IOssStorageService> ossStorageServiceList;

    private static final Map<String, IOssStorageService> OSS_STORAGE_SERVICE_MAP = new ConcurrentHashMap<>();

    public WebdavConfig(FileProperties fileProperties, MyRealm myRealm, WebdavAuthenticator webdavAuthenticator, MyWebdavServlet myWebdavServlet, ApplicationContext context, List<IOssStorageService> ossStorageServiceList) {
        this.fileProperties = fileProperties;
        this.myRealm = myRealm;
        this.webdavAuthenticator = webdavAuthenticator;
        this.myWebdavServlet = myWebdavServlet;
        this.context = context;
        this.ossStorageServiceList = ossStorageServiceList;

        for (IOssStorageService iOssStorageService : this.ossStorageServiceList) {
            OSS_STORAGE_SERVICE_MAP.put(iOssStorageService.getPlatform().getKey(), iOssStorageService);
        }

        // TODO 临时插入
        BucketInfo bucketInfo = new BucketInfo();
        bucketInfo.setPlatform(PlatformOSS.ALIYUN);
        bucketInfo.setBucketName("jmalcloud");
        bucketInfo.setUsername("jmal");
        bucketInfo.setFolderName("aliyunoss");
        CaffeineUtil.setOssDiameterPrefixCache("/jmal/aliyunoss", bucketInfo);

    }

    public static <T> T getBean(Class<T> requiredType) {
        return context.getBean(requiredType);
    }

    @Bean
    public ServletRegistrationBean<MyWebdavServlet> webdavServlet() {
        ServletRegistrationBean<MyWebdavServlet> registration = new ServletRegistrationBean<>(myWebdavServlet, fileProperties.getWebDavPrefixPath() + "/*");
        registration.setName("WebDAV servlet");
        registration.setServlet(myWebdavServlet);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("listings", String.valueOf(true));
        registration.addInitParameter("readonly", String.valueOf(false));
        registration.addInitParameter("debug", String.valueOf(1));
        return registration;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            // 创建一个新的WebResourceRoot实例
            MyStandardRoot standardRoot = new MyStandardRoot(context);
            // 自定义静态资源的位置
            standardRoot.addPreResources(new FileResourceSet(standardRoot, fileProperties.getRootDir(), OSS_STORAGE_SERVICE_MAP));
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
