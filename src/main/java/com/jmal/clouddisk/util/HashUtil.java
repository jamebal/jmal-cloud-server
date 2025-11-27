package com.jmal.clouddisk.util;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HashUtil {

    private static final HashFunction SHA256 = Hashing.sha256();

    /**
     * 轻量级文件 ETag（零文件 I/O）
     */
    public static String fileEtag(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        long size = file.length();
        long lastModified = file.lastModified();

        // 将两个 long 转为 16 字节数组
        byte[] bytes = ByteBuffer
                .allocate(16)
                .putLong(size)
                .putLong(lastModified)
                .array();

        return SHA256.hashBytes(bytes).toString();
    }

    public static String sha256(String str) {
        return SHA256.hashString(str, StandardCharsets.UTF_8).toString();
    }
}
