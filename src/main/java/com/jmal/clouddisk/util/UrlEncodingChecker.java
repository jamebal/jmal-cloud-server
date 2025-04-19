package com.jmal.clouddisk.util;

import cn.hutool.core.util.URLUtil;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlEncodingChecker {

    // 正则表达式：匹配 % 后跟两个十六进制字符 (如 %20, %A1)
    private static final Pattern URL_ENCODING_PATTERN = Pattern.compile("%[0-9A-Fa-f]{2}");

    /**
     * 判断字符串是否可能已经过 URL 编码。
     *
     * @param str 要检查的字符串
     * @return true 如果字符串可能已编码（包含编码模式且解码后不同），否则 false
     */
    public static boolean isUrlEncoded(String str) {
        if (str == null || str.isEmpty()) {
            return false;  // 空字符串不认为是已编码的
        }

        // 步骤1: 检查字符串中是否包含 URL 编码模式
        Matcher matcher = URL_ENCODING_PATTERN.matcher(str);
        boolean containsEncoding = matcher.find();  // 如果找到 %XX 模式

        if (!containsEncoding) {
            return false;  // 不包含编码模式，直接返回 false
        }

        // 步骤2: 尝试解码字符串
        try {
            String decodedStr = URLUtil.encode(str, StandardCharsets.UTF_8);
            // 如果解码后与原字符串不同，则可能是已编码的
            return !str.equals(decodedStr);
        } catch (IllegalArgumentException e) {
            // 解码时如果有无效的 % 编码，会抛出这个异常
            return true;  // 认为可能是已编码的，但有错误
        }
    }

    public static void main(String[] args) {
        // 测试示例
        String test1 = "Hello%20World";  // 已编码
        String test2 = "Hello World";    // 未编码
        String test3 = "100%";           // 可能误判，但包含 %，需检查
        String test4 = "%Invalid";       // 无效编码

        System.out.println(test1 + " is encoded: " + isUrlEncoded(test1));  // true
        System.out.println(test2 + " is encoded: " + isUrlEncoded(test2));  // false
        System.out.println(test3 + " is encoded: " + isUrlEncoded(test3));  // true (因为解码后不同)
        System.out.println(test4 + " is encoded: " + isUrlEncoded(test4));  // true (解码失败)
    }
}
