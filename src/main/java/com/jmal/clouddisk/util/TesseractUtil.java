package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
public class TesseractUtil {
    public static void setTesseractLibPath() {
        String libPath = findTesseractLibPath();
        if (libPath != null) {
            System.setProperty("jna.library.path", libPath);
            log.info("Set Tesseract lib path: {}", libPath);
        } else {
            log.info("Tesseract lib path not found");
        }
    }

    private static String findTesseractLibPath() {
        // 基础路径
        String basePath = "/opt/homebrew/Cellar/tesseract";
        Path baseDir = Paths.get(basePath);

        // 检查基础目录是否存在
        if (!Files.exists(baseDir)) {
            return null;
        }

        try {
            // 查找最新版本的目录
            Optional<Path> latestVersion = Files.list(baseDir)
                    .filter(Files::isDirectory)
                    .max((p1, p2) -> {
                        String v1 = p1.getFileName().toString();
                        String v2 = p2.getFileName().toString();
                        return compareVersions(v1, v2);
                    });

            if (latestVersion.isPresent()) {
                Path libPath = latestVersion.get().resolve("lib");
                return Files.exists(libPath) ? libPath.toString() : null;
            }
        } catch (IOException e) {
            log.error("Failed to find Tesseract lib path", e);
        }

        return null;
    }

    // 版本号比较工具方法
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("[._]");
        String[] parts2 = v2.split("[._]");

        int length = Math.min(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            try {
                int num1 = Integer.parseInt(parts1[i]);
                int num2 = Integer.parseInt(parts2[i]);
                if (num1 != num2) {
                    return num1 - num2;
                }
            } catch (NumberFormatException e) {
                // 如果解析失败，按字符串比较
                int comp = parts1[i].compareTo(parts2[i]);
                if (comp != 0) return comp;
            }
        }
        return parts1.length - parts2.length;
    }
}
