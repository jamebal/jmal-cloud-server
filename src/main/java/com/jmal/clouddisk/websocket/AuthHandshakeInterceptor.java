package com.jmal.clouddisk.websocket;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
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

    private final AuthInterceptor authInterceptor;

    public AuthHandshakeInterceptor(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest serverHttpRequest, @NotNull ServerHttpResponse serverHttpResponse, @NotNull WebSocketHandler webSocketHandler, @NotNull Map<String, Object> map) {
        ServletServerHttpRequest request = (ServletServerHttpRequest) serverHttpRequest;
        String jmalToken = request.getServletRequest().getParameter("jmal-token");
        String name = request.getServletRequest().getParameter("name");
        return !CharSequenceUtil.isBlank(authInterceptor.getUserNameByJmalToken(null, null, jmalToken, name));
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception exception) {
        // 啥都没干
    }
}
