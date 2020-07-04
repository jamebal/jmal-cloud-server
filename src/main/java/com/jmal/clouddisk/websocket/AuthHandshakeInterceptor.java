package com.jmal.clouddisk.websocket;

import com.jmal.clouddisk.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * @Description websocket 拦截器
 * @Author jmal
 * @Date 2020-07-01 14:12
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    AuthInterceptor authInterceptor;

    @Override
    public boolean beforeHandshake(ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse, WebSocketHandler webSocketHandler, Map<String, Object> map) throws Exception {
        ServletServerHttpRequest req = (ServletServerHttpRequest) serverHttpRequest;
        //获取token认证
        String jmalToken = req.getServletRequest().getParameter("jmal-token");
        if (authInterceptor.checkToken(jmalToken)) {
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse, WebSocketHandler webSocketHandler, Exception e) {

    }
}
