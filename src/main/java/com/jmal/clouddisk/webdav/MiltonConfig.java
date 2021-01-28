package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import io.milton.config.HttpManagerBuilder;
import io.milton.http.*;
import io.milton.http.fs.FileSystemResourceFactory;
import io.milton.http.fs.NullSecurityManager;
import io.milton.http.http11.DefaultETagGenerator;
import io.milton.http.http11.DefaultHttp11ResponseHandler;
import io.milton.http.http11.SimpleContentGenerator;
import io.milton.http.http11.auth.PreAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Component
public class MiltonConfig {

    @Autowired
    private MyAuthorizationHandler myAuthorizationHandler;

    @Autowired
    private FileProperties fileProperties;

    private ResourceFactory resourceFactory() {
        FileSystemResourceFactory factory = new FileSystemResourceFactory();
        factory.setAllowDirectoryBrowsing(true);
        factory.setRoot(new File(fileProperties.getRootDir()));
        factory.setSecurityManager(new NullSecurityManager());
        return factory;
    }

    @Bean
    HttpManagerBuilder httpManagerBuilder() {
        HttpManagerBuilder builder = new HttpManagerBuilder();
        builder.setResourceFactory(resourceFactory());
        builder.setBuffering(DefaultHttp11ResponseHandler.BUFFERING.whenNeeded);
        builder.setEnableCompression(false);
        builder.setEnableOptionsAuth(true);
        List<AuthenticationHandler> authenticationHandlers = new ArrayList<>();
        authenticationHandlers.add(myAuthorizationHandler);
        AuthenticationService authenticationService = new AuthenticationService(authenticationHandlers);
        DefaultHttp11ResponseHandler rh = new DefaultHttp11ResponseHandler(authenticationService, new DefaultETagGenerator(), new SimpleContentGenerator());
        List<Filter> list = new ArrayList<>();
        list.add(new PreAuthenticationFilter(rh, authenticationHandlers));
        list.add(new StandardFilter());
        builder.setFilters(list);
        return builder;
    }
}
