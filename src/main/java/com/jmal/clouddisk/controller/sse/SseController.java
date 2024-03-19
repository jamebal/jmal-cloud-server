package com.jmal.clouddisk.controller.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Slf4j
public class SseController {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestParam String username) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(username, emitter);
        emitter.onCompletion(() -> emitters.remove(username));
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
        SseEmitter emitter = emitters.get(username);
        if (emitter != null) {
            try {
                emitter.send(message);
            } catch (IOException e) {
                log.error("Failed to send event to user: {}", username, e);
            }
        }
    }
}

