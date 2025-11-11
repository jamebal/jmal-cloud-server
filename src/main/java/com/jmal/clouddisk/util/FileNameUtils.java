package com.jmal.clouddisk.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;

public class FileNameUtils {

    /**
     * 安全地解码一个可能经过URL编码的文件名或路径。
     * 这个方法修复了 java.net.URLDecoder 会错误地将 '+' 转换为空格的问题。
     * 它正确地处理了 '+' 字符，同时解码其他的 %xx 序列。
     *
     * @param encodedPath 经过编码的路径字符串
     * @return 解码后的路径字符串
     */
    public static String safeDecode(String encodedPath) {
        if (encodedPath == null || encodedPath.isEmpty()) {
            return encodedPath;
        }
        if (UrlEncodingChecker.isUrlEncoded(encodedPath)) {
            // 这样，URLDecoder在解码时，会把 '%2B' 转换回 '+'，而不是转换成空格。
            // 同时，它仍然能正确地将真正的空格编码 '%20' 转换为空格。
            String protectedPath = encodedPath.replace("+", "%2B");
            return URLUtil.decode(protectedPath);
        }
        return encodedPath;
    }

    public static String validateAndSanitizeFilename(String filename) {
        if (StrUtil.isBlank(filename)) {
            throw new CommonException(ExceptionType.PARAMETERS_VALUE.getCode(), "文件名不能为空");
        }

        // 使用正则一次性替换所有危险字符
        String sanitized = filename
                .replace("..", "_")
                .replaceAll("[/\\\\;|&$`<>\"'*?:]", "_");

        // 去除首尾空格和点
        sanitized = sanitized.trim().replaceAll("^\\.+|\\.+$", "");

        if (StrUtil.isBlank(sanitized)) {
            throw new CommonException(ExceptionType.PARAMETERS_VALUE.getCode(), "文件名包含非法字符");
        }

        // 限制长度
        if (sanitized.length() > 255) {
            String ext = FileUtil.getSuffix(sanitized);
            if (StrUtil.isNotBlank(ext)) {
                int maxNameLength = 254 - ext.length();
                String nameWithoutExt = StrUtil.removeSuffix(sanitized, "." + ext);
                sanitized = nameWithoutExt.substring(0, Math.min(nameWithoutExt.length(), maxNameLength)) + "." + ext;
            } else {
                sanitized = sanitized.substring(0, 255);
            }
        }

        return sanitized;
    }

}
