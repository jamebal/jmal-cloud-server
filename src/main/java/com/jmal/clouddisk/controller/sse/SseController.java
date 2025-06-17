package com.jmal.clouddisk.controller.sse;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@RestController
@Slf4j
public class SseController {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30分钟
    private static final int MAX_CONNECTIONS_PER_USER = 128;

    /**
     * SseEmitter 容器
     * key: uuid (客户端唯一标识)
     * value: SseEmitter
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 用户连接映射
     * key: username
     * value: 该用户所有连接的 uuid 集合
     */
    private final Map<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    /**
     * uuid到用户的反向映射，用于高效清理
     * key: uuid
     * value: username
     */
    private final Map<String, String> uuidToUser = new ConcurrentHashMap<>();

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events(@RequestParam String username, @RequestParam String uuid) {
        // 使用 computeIfAbsent 保证原子性操作，为用户创建连接集合
        Set<String> uuids = userConnections.computeIfAbsent(username, k -> new CopyOnWriteArraySet<>());

        // 检查用户连接数是否超限
        if (uuids.size() >= MAX_CONNECTIONS_PER_USER) {
            log.warn("User '{}' has reached the maximum connection limit ({})", username, MAX_CONNECTIONS_PER_USER);
            // 可以返回一个立即完成的 SseEmitter 并携带错误信息
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("Connection limit exceeded"));
                errorEmitter.complete();
            } catch (IOException e) {
                // ignore
            }
            return errorEmitter;
        }

        SseEmitter emitter = getSseEmitter(uuid);

        // 存储连接信息
        uuids.add(uuid);
        uuidToUser.put(uuid, username);
        emitters.put(uuid, emitter);

        log.debug("Client connected: user='{}', uuid='{}'. Total connections for user: {}", username, uuid, uuids.size());

        // 连接成功后可以立即发送一条欢迎消息
        try {
            emitter.send(SseEmitter.event().name("connected").data("Connection established"));
        } catch (IOException e) {
            log.debug("Failed to send initial connection message to {}: {}", uuid, e.getMessage());
            this.removeClient(uuid);
        }

        return emitter;
    }

    @NotNull
    private SseEmitter getSseEmitter(String uuid) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 注册回调，统一处理连接关闭的所有情况（完成、超时、错误）
        emitter.onCompletion(() -> {
            log.debug("SseEmitter completed for uuid: {}", uuid);
            this.removeClient(uuid);
        });
        emitter.onTimeout(() -> {
            log.debug("SseEmitter timed out for uuid: {}", uuid);
            // SseEmitter 超时会自动触发 onCompletion，所以这里只需要日志记录，无需重复调用 removeClient
            // 如果希望超时后立即完成，可以调用 emitter.complete()，它会触发 onCompletion
        });
        emitter.onError(throwable -> {
            log.debug("SseEmitter error for uuid: {}. Error: {}", uuid, throwable.getMessage());
            // onError 发生后，Spring 也会确保 onCompletion 被调用，因此也无需重复调用 removeClient
        });
        return emitter;
    }

    @PostMapping("/send")
    public void sendEvent(@RequestBody Message message) {
        String username = message.getUsername();
        Set<String> uuids = userConnections.get(username);
        if (uuids != null && !uuids.isEmpty()) {
            // 遍历时复制集合，防止在发送过程中集合被修改
            for (String uuid : Set.copyOf(uuids)) {
                sendMessage(uuid, message);
            }
        }
    }

    private void sendMessage(String uuid, Object message) {
        SseEmitter emitter = emitters.get(uuid);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (Exception e) {
                // 发送失败，不需要手动清理，因为 onError 和 onCompletion 回调会处理
                log.debug("Failed to send message to client {}. It might be disconnected. Error: {}", uuid, e.getMessage());
            }
        }
    }

    /**
     * 统一的客户端移除方法，保证线程安全和数据一致性
     * @param uuid 客户端唯一标识
     */
    private void removeClient(String uuid) {
        // 1. 移除 emitter
        emitters.remove(uuid);

        // 2. 移除 uuid 到 username 的映射
        String username = uuidToUser.remove(uuid);

        // 3. 从用户的连接集合中移除 uuid
        if (username != null) {
            Set<String> uuids = userConnections.get(username);
            if (uuids != null) {
                uuids.remove(uuid);
                // 4. 如果用户已无任何连接，则从 userConnections 中移除该用户
                if (uuids.isEmpty()) {
                    userConnections.remove(username);
                    log.debug("User '{}' has no active connections, removed from tracking.", username);
                }
            }
        }
        log.debug("Client disconnected and cleaned up: uuid='{}'", uuid);
    }


    /**
     * 每30秒发送一次心跳，以保持连接活跃并检测断开的客户端
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        log.trace("Sending heartbeat to {} clients...", emitters.size());
        // 遍历所有 emitter 发送心跳
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String uuid = entry.getKey();
            SseEmitter emitter = entry.getValue();
            try {
                // 发送一个 SSE 注释行，这不会在客户端触发 "message" 事件
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                // 发送失败意味着客户端已断开
                // 无需在此处执行清理，SseEmitter 的 onError/onCompletion 回调会负责
                log.debug("Heartbeat failed for client {}, will be removed by callbacks.", uuid);
            }
        }
    }
}
