package com.jmal.clouddisk.util;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.File;

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
        long lastModified = file. lastModified();

        // 将两个 long 转为 16 字节数组
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (size >>> (56 - i * 8));
            bytes[i + 8] = (byte) (lastModified >>> (56 - i * 8));
        }

        return SHA256.hashBytes(bytes).toString();
    }

    public static String sha256(String str) {
        return SHA256.hashString(str, java.nio.charset.StandardCharsets.UTF_8).toString();
    }
}
