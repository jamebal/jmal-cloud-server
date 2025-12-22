package com.jmal.clouddisk.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

public final class IPUtil {

    private IPUtil() {
    }

    public static final int REGION_LENGTH = 5;
    public static final Pattern SPLIT_PATTERN = Pattern.compile("\\|");

    private static final String[] IP_HEADERS = {
            "CF-Connecting-IP",
            "X-Real-IP",
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    };

    /**
     * 获取客户端真实IP
     * 自动处理大小写不敏感的请求头
     *
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    public static String getClientIP(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                return extractFirstIp(ip);
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 提取第一个IP（处理逗号分隔的多IP情况）
     */
    private static String extractFirstIp(String ip) {
        if (ip.contains(",")) {
            int commaIndex = ip.indexOf(',');
            return ip.substring(0, commaIndex).trim();
        }
        return ip.trim();
    }

    /**
     * 检查IP是否有效
     */
    private static boolean isValidIp(String ip) {
        return ip != null
                && !ip.isEmpty()
                && !"unknown".equalsIgnoreCase(ip);
    }
}
