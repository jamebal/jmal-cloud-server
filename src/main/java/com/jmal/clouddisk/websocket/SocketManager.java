package com.jmal.clouddisk.websocket;

import cn.hutool.core.text.CharSequenceUtil;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * socket管理器
 *
 * @author jmal
 */
public class SocketManager {

    private SocketManager() {

    }
    private static final ConcurrentHashMap<String, WebSocketSession> manager = new ConcurrentHashMap<>();

    static void add(String key, WebSocketSession webSocketSession) {
        manager.put(key, webSocketSession);
    }

    static void remove(String key) {
        manager.remove(key);
    }

    public static WebSocketSession get(String key) {
        if(CharSequenceUtil.isBlank(key)){
            return null;
        }
        return manager.get(key);
    }

    public static ConcurrentMap<String, WebSocketSession> getManager(){
        return manager;
    }


}
