package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import io.milton.http.ResourceFactory;
import io.milton.http.fs.FileSystemResourceFactory;
import io.milton.http.http11.DefaultHttp11ResponseHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.File;


@Component
public class MiltonConfig {

    @Autowired
    private MySimpleSecurityManager mySimpleSecurityManager;

    @Autowired
    private FileProperties fileProperties;

    private ResourceFactory resourceFactory() {
        FileSystemResourceFactory factory = new FileSystemResourceFactory();
        factory.setAllowDirectoryBrowsing(true);
        factory.setRoot(new File(fileProperties.getRootDir()));
        factory.setSecurityManager(mySimpleSecurityManager);
        factory.setContextPath(fileProperties.getWebDavPrefix());
        return factory;
    }

    @Bean
    MyHttpManagerBuilder httpManagerBuilder() {
        MyHttpManagerBuilder builder = new MyHttpManagerBuilder();
        builder.setResourceFactory(resourceFactory());
        builder.setBuffering(DefaultHttp11ResponseHandler.BUFFERING.whenNeeded);
        builder.setEnableCompression(false);
        builder.setEnableOptionsAuth(true);
        builder.setEnableBasicAuth(true);
        builder.setEnableCookieAuth(false);
        return builder;
    }
}
