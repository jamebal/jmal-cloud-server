package com.jmal.clouddisk.websocket;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * socket管理器
 *
 * @author jmal
 */
@Slf4j
public class SocketManager {
    private static ConcurrentHashMap<String, WebSocketSession> manager = new ConcurrentHashMap<>();

    static void add(String key, WebSocketSession webSocketSession) {
        manager.put(key, webSocketSession);
    }

    static void remove(String key) {
        manager.remove(key);
    }

    public static WebSocketSession get(String key) {
        if(StrUtil.isBlank(key)){
            return null;
        }
        return manager.get(key);
    }

    public static ConcurrentHashMap<String, WebSocketSession> getManager(){
        return manager;
    }


}
