package com.jmal.clouddisk.websocket;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
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
        ServletServerHttpRequest servletServerHttpRequest = (ServletServerHttpRequest) serverHttpRequest;
        ServletServerHttpResponse response = (ServletServerHttpResponse) serverHttpResponse;
        HttpServletRequest request = servletServerHttpRequest.getServletRequest();
        String jmalToken = AuthInterceptor.getCookie(request, AuthInterceptor.JMAL_TOKEN);
        if (CharSequenceUtil.isBlank(jmalToken)) {
            return false;
        }
        String name = AuthInterceptor.getCookie(request, "username");
        if (CharSequenceUtil.isBlank(name)) {
            return false;
        }
        return !CharSequenceUtil.isBlank(authInterceptor.getUserNameByJmalToken(request, response.getServletResponse(), jmalToken, name));
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception exception) {
        // 啥都没干
    }

}
