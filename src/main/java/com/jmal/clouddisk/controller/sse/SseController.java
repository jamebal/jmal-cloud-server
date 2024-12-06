package com.jmal.clouddisk.controller.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Slf4j
public class SseController {

    /**
     * key: uuid
     * value: SseEmitter
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * key: username
     * value: uuid list
     */
    private final Map<String, Set<String>> users = new ConcurrentHashMap<>();

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestParam String username, @RequestParam String uuid) {
        if (users.containsKey(username)) {
            users.get(username).add(uuid);
        } else {
            Set<String> uuids = ConcurrentHashMap.newKeySet(32);
            if (uuids.size() > 30) {
                return null;
            }
            uuids.add(uuid);
            users.put(username, uuids);
        }
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(uuid, emitter);
        emitter.onCompletion(() -> emitters.remove(uuid));
        emitter.onError((e) -> removeUuid(username, uuid));
        emitter.onTimeout(() -> removeUuid(username, uuid));
        return emitter;
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<String> handleAsyncRequestTimeoutException() {
        // 处理异步请求超时异常,例如记录日志或返回自定义响应

        // 设置响应头的 Content-Type 为 "text/event-stream"
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);

        // 返回一个空的 SSE 事件作为响应
        String emptyEvent = "event: timeout\ndata: \n\n";
        return new ResponseEntity<>(emptyEvent, headers, HttpStatus.OK);
    }

    @PostMapping("/send")
    public void sendEvent(@RequestBody Message message) {
        String username = message.getUsername();
        if (users.containsKey(username)) {
            users.get(username).forEach(uuid -> sendMessage(message, username, uuid));
        }
    }

    private void sendMessage(Object message, String username, String uuid) {
        try {
            SseEmitter emitter = emitters.get(uuid);
            if (emitter != null) {
                try {
                    emitter.send(message);
                } catch (Exception e) {
                    removeUuid(username, uuid);
                }
            }
        } catch (Throwable e) {
            log.warn("Failed to send message to client {}: {}", uuid, e.getMessage());
        }
    }


    private void removeUuid(String username, String uuid) {
        try {
            emitters.remove(uuid);
            Set<String> uuids = users.get(username);
            if (uuids != null) {
                uuids.remove(uuid);
                if (uuids.isEmpty()) {
                    users.remove(username);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to remove uuid {}: {}", uuid, e.getMessage());
        }
    }

}

