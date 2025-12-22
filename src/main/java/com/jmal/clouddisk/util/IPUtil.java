package com.jmal.clouddisk.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

public class IPUtil {

    public static final int REGION_LENGTH = 5;
    public static final Pattern SPLIT_PATTERN = Pattern.compile("\\|");

    private static final String[] IP_HEADERS = {
        "CF-Connecting-IP",
        "X-Real-IP",
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP"
    };

    public static String getClientIP(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                return extractFirstIp(ip, header);
            }
        }
        return request.getRemoteAddr();
    }

    private static String extractFirstIp(String ip, String header) {
        if (header.contains("Forwarded") && ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        return ip;
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}
