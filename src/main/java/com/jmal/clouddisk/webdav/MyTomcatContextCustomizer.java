package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.webdav.resource.FileResourceSet;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.stereotype.Component;

/**
 * 【新增部分】创建一个独立的、具体的 Spring 组件来实现 TomcatContextCustomizer 接口。
 * 这样做可以完全避免在 Bean 定义中使用复杂的 Lambda 表达式，对 GraalVM Native Image 非常友好。
 */
@Component
public class MyTomcatContextCustomizer implements TomcatContextCustomizer {

    private final FileProperties fileProperties;
    private final WebdavAuthenticator webdavAuthenticator;
    private final MyRealm myRealm;

    public MyTomcatContextCustomizer(FileProperties fileProperties, WebdavAuthenticator webdavAuthenticator, MyRealm myRealm) {
        this.fileProperties = fileProperties;
        this.webdavAuthenticator = webdavAuthenticator;
        this.myRealm = myRealm;
    }

    @Override
    public void customize(Context context) {
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
    }
}
