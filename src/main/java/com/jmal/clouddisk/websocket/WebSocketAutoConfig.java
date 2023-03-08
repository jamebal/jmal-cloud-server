package com.jmal.clouddisk.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocketAutoConfig
 *
 * @author jmal
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketAutoConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketDecoratorFactory webSocketDecoratorFactory;

    private final PrincipalHandshakeHandler principalHandshakeHandler;

    private final HandshakeInterceptor handshakeInterceptor;

    public WebSocketAutoConfig(WebSocketDecoratorFactory webSocketDecoratorFactory, PrincipalHandshakeHandler principalHandshakeHandler, HandshakeInterceptor handshakeInterceptor) {
        this.webSocketDecoratorFactory = webSocketDecoratorFactory;
        this.principalHandshakeHandler = principalHandshakeHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }


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

