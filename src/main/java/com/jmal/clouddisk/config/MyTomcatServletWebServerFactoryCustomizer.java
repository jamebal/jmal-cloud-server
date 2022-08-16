package com.jmal.clouddisk.config;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.TomcatServletWebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.stereotype.Component;

/**
 * @author jmal
 */
@Component
public class MyTomcatServletWebServerFactoryCustomizer extends TomcatServletWebServerFactoryCustomizer {

    public MyTomcatServletWebServerFactoryCustomizer(ServerProperties serverProperties) {
        super(serverProperties);
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        super.customize(factory);
        factory.addConnectorCustomizers(connector -> {
            connector.setProperty("relaxedPathChars", "|{}[]");
            connector.setProperty("relaxedQueryChars", "|{}[]");
        });
    }
}
