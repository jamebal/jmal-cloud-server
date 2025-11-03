package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jmal
 * @Description 系统信息
 * @date 2022/4/13 14:13
 */
@Slf4j
public class SystemUtil {

    private static final String LATEST_RELEASE_URL = "https://github.com/jamebal/jmal-cloud-view/releases/latest";
    private static final String ALTERNATE_LATEST_RELEASE_URL = "https://xget.jmalx.com/gh/jamebal/jmal-cloud-view/releases/latest";
    private static final String RELEASE_TAG_URL_PREFIX = "https://github.com/jamebal/jmal-cloud-view/releases/tag/";
    private static final String USER_AGENT = "jmal-cloud-server";

    /**
     * 获取硬盘可用空间(Gb)
     */
    public static long getFreeSpace() {
        File win = new File("/");
        if (win.exists()) {
            long freeSpace = win.getFreeSpace();
            return freeSpace / 1024 / 1024 / 1024;
        }
        return 0;
    }

    /**
     * 获取最新版本号
     *
     * @return String
     */
    public static String getNewVersion() {
        String[] urls = {LATEST_RELEASE_URL, ALTERNATE_LATEST_RELEASE_URL};

        try (HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            for (String url : urls) {
                try {
                    String version = getLastVersion(httpClient, url);
                    if (version != null) {
                        return version;
                    }
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.debug("从URL '{}' 获取最新版本失败: {}", url, e.getMessage());
                }
            }
        }

        log.warn("获取最新版本失败，请检查网络连接");
        return null;
    }

    @Nullable
    private static String getLastVersion(HttpClient httpClient, String url) throws IOException, InterruptedException {
        HttpRequest altRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> altResponse = httpClient.send(altRequest, HttpResponse.BodyHandlers.ofString());

        if (altResponse.statusCode() >= 300 && altResponse.statusCode() < 400) {
            String location = altResponse.headers().firstValue("Location").orElse(null);
            if (location != null && location.contains(RELEASE_TAG_URL_PREFIX)) {
                return location.substring(location.lastIndexOf("/") + 1);
            }
        }

        if (altResponse.statusCode() == 200) {
            String body = altResponse.body();
            Pattern regex = Pattern.compile(Pattern.quote(RELEASE_TAG_URL_PREFIX) + "([^\\s\"']+)");
            Matcher matcher = regex.matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
