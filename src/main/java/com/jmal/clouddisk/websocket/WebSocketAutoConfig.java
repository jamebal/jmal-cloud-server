package com.jmal.clouddisk.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocketAutoConfig
 *
 * @author jmal
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketAutoConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketDecoratorFactory webSocketDecoratorFactory;

    @Autowired
    private PrincipalHandshakeHandler principalHandshakeHandler;

    @Autowired
    private HandshakeInterceptor handshakeInterceptor;


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        /*
         * mq表示 你前端到时要对应url映射
         */
        registry.addEndpoint("/mq")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(principalHandshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        /*
         * queue 点对点
         * topic 广播
         * user 点对点前缀
         */
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(webSocketDecoratorFactory);
    }
}

