package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * @author jmal
 * @Description 系统信息
 * @date 2022/4/13 14:13
 */
@Slf4j
public class SystemUtil {

    private static final String LATEST_RELEASE_URL = "https://github.com/jamebal/jmal-cloud-view/releases/latest";
    private static final String USER_AGENT = "jmal-cloud-server";

    /**
     * 获取硬盘可用空间(Gb)
     */
    public static long getFreeSpace(){
        File win = new File("/");
        if (win.exists()) {
            long freeSpace = win.getFreeSpace();
            return freeSpace/1024/1024/1024;
        }
        return 0;
    }

    /**
     * 获取最新版本号
     * @return String
     */
    public static String getNewVersion() {
        try(HttpClient httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            // 检查是否是重定向响应（301, 302, 303, 307, 308）
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location != null && location.contains("/tag/")) {
                    return location.substring(location.lastIndexOf("/") + 1);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("获取最新版本失败: {}", e.getMessage());
        }
        return null;
    }
}
