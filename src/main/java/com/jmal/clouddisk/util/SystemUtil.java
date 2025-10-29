package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
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
     * 获取最新版本号（通过 HTTP 重定向）
     * @return String
     */
    public static String getNewVersion() {
        try(HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)  // 不自动跟随重定向
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://github.com/jamebal/jmal-cloud-view/releases/latest"))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "jmal-cloud-server")
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            // 检查是否是重定向响应（301, 302, 303, 307, 308）
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location != null && location.contains("/tag/")) {
                    return location.substring(location.lastIndexOf("/") + 1);
                }
            }
        } catch (Exception e) {
            log.warn("获取最新版本失败: {}", e.getMessage());
        }
        return null;
    }
}
