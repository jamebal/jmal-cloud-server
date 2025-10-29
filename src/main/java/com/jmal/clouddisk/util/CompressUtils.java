package com.jmal.clouddisk.util;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import lombok.extern.slf4j.Slf4j;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 压缩解压工具类（基于 7z 命令）
 * 安全特性:
 * - 防止路径遍历攻击 (Path Traversal)
 * - 防止符号链接攻击 (Symlink Attack)
 * - 防止 ZIP 炸弹攻击 (Zip Bomb)
 * - 防止命令注入
 * - 解压后安全验证
 *
 * @author jmal
 * @date 2025-10-29
 */
@Slf4j
public class CompressUtils {

    // ==================== 安全配置常量 ====================

    /** 命令执行超时时间（分钟） */
    private static final long COMMAND_TIMEOUT_MINUTES = 30;

    /** 读取缓冲区大小 */
    private static final int BUFFER_SIZE = 8192;

    /** 最大文件数量（防止 ZIP 炸弹） */
    private static final int MAX_FILE_COUNT = 50000;

    /** 解压总大小限制: 50GB */
    private static final long MAX_TOTAL_SIZE = 50L * 1024 * 1024 * 1024;

    // ==================== 压缩方法 ====================

    /**
     * 压缩文件/文件夹到输出流
     *
     * @param paths 要压缩的路径列表
     * @param outputStream 输出流
     */
    public static void compress(List<Path> paths, OutputStream outputStream) {
        if (paths == null || paths.isEmpty()) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "压缩路径列表不能为空");
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            zipOut.setEncoding(StandardCharsets.UTF_8.name());

            for (Path path : paths) {
                if (!Files.exists(path)) {
                    log.warn("路径不存在，跳过: {}", path);
                    continue;
                }

                // 检查符号链接
                if (Files.isSymbolicLink(path)) {
                    log.warn("跳过符号链接: {}", path);
                    continue;
                }

                if (Files.isDirectory(path)) {
                    addDirectoryToZip(path.getParent(), path, zipOut);
                } else {
                    addFileToZip(path.getParent(), path, zipOut);
                }
            }

            zipOut.finish();

        } catch (IOException e) {
            log.error("压缩文件失败", e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "压缩文件失败: " + e.getMessage());
        }
    }

    /**
     * 添加目录到 ZIP
     */
    private static void addDirectoryToZip(Path rootDir, Path sourceDir, ZipOutputStream zipOut) throws IOException {

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                // 跳过符号链接
                if (Files.isSymbolicLink(file)) {
                    log.warn("跳过符号链接文件: {}", file);
                    return FileVisitResult.CONTINUE;
                }

                addFileToZip(rootDir, file, zipOut);
                return FileVisitResult.CONTINUE;
            }

            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                // 跳过符号链接目录
                if (Files.isSymbolicLink(dir)) {
                    log.warn("跳过符号链接目录: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                if (!dir.equals(sourceDir)) {
                    addDirectoryEntryToZip(rootDir, dir, zipOut);
                }
                return FileVisitResult.CONTINUE;
            }

            @NotNull
            @Override
            public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
                log.warn("访问文件失败: {}, 原因: {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 添加文件到 ZIP
     */
    private static void addFileToZip(Path rootDir, Path file, ZipOutputStream zipOut) throws IOException {
        String zipEntryName = rootDir.relativize(file).toString().replace("\\", "/");
        ZipEntry zipEntry = new ZipEntry(zipEntryName);
        zipOut.putNextEntry(zipEntry);

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) > 0) {
                zipOut.write(buffer, 0, len);
            }
        }

        zipOut.closeEntry();
    }

    /**
     * 添加目录条目到 ZIP
     */
    private static void addDirectoryEntryToZip(Path rootDir, Path dir, ZipOutputStream zipOut) throws IOException {
        String zipEntryName = rootDir.relativize(dir).toString().replace("\\", "/") + "/";
        ZipEntry zipEntry = new ZipEntry(zipEntryName);
        zipOut.putNextEntry(zipEntry);
        zipOut.closeEntry();
    }

    // ==================== 解压方法 ====================

    /**
     * 解压文件
     *
     * @param filePath 压缩文件路径
     * @param outputDir 输出目录
     * @param isWrite 是否实际写入文件（false 仅创建空文件结构）
     */
    public static void decompress(String filePath, String outputDir, boolean isWrite)
            throws IOException, InterruptedException {

        File file = new File(filePath);
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND.getCode(), "文件不存在: " + filePath);
        }

        // 验证输出目录安全性
        Path outputPath = validateAndCreateOutputDirectory(outputDir);

        String compressionType = DetectArchiveType.detectType(filePath);
        log.debug("检测到压缩类型: {}, 文件: {}", compressionType, file.getName());

        if (!isWrite) {
            // 仅创建空文件结构
            extractEmptyFiles(file, outputPath.toFile());
            return;
        }

        // 验证压缩类型
        if (!isSupportedType(compressionType)) {
            throw new CommonException(ExceptionType.UNRECOGNIZED_FILE.getCode(),
                    "不支持的压缩格式: " + compressionType);
        }

        // 使用 7z 解压
        decompress7z(file, outputPath);

        // 解压后安全验证
        postExtractionValidation(outputPath);

        log.debug("✅ 解压完成: {}", file.getName());
    }

    /**
     * 检查是否是支持的压缩类型
     */
    private static boolean isSupportedType(String type) {
        return switch (type) {
            case "jar", "7z", "zip", "rar5", "rar", "tar", "gzip", "bzip2", "xz", "iso" -> true;
            default -> false;
        };
    }

    /**
     * 验证并创建输出目录
     */
    private static Path validateAndCreateOutputDirectory(String outputDir) throws IOException {
        if (outputDir == null || outputDir.trim().isEmpty()) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "输出目录不能为空");
        }

        // 验证路径安全性（防止命令注入）
        if (!isValidOutputPath(outputDir)) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "输出目录包含非法字符: " + outputDir);
        }

        Path path = Paths.get(outputDir).toAbsolutePath().normalize();

        // 创建目录
        Files.createDirectories(path);

        // 确保是目录
        if (!Files.isDirectory(path)) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "输出路径不是目录: " + outputDir);
        }

        // 检查是否是符号链接
        if (Files.isSymbolicLink(path)) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "输出目录不能是符号链接: " + outputDir);
        }

        return path.toRealPath();
    }

    /**
     * 验证输出路径是否安全（防止命令注入）
     */
    private static boolean isValidOutputPath(String path) {
        // 允许字母、数字、常见路径字符
        // 禁止 ; & | $ ` < > 等可能导致命令注入的字符
        return path.matches("^[a-zA-Z0-9/._\\-\\\\: ]+$");
    }

    /**
     * 使用 7z 命令解压
     */
    private static void decompress7z(File archiveFile, Path outputPath) throws IOException, InterruptedException {
        String outputDir = outputPath.toString();

        ProcessBuilder pb = new ProcessBuilder(
                "7z",
                "x",                                  // 解压并保留目录结构
                archiveFile.getAbsolutePath(),        // 输入文件
                "-o" + outputDir,                     // 输出目录（-o 和路径之间不能有空格）
                "-y",                                 // 自动回答 yes
                "-aos",                               // 跳过已存在的文件
                "-bb0",                               // 最小日志级别
                "-bd",                                // 禁用进度指示器
                "-snl",                               // 不存储符号链接
                "-snh",                               // 不存储硬链接
                "-sni",                               // 不存储 Alternate Data Streams (Windows)
                "-xr!PaxHeader",                      // 排除所有 PaxHeader 文件
                "-xr!@LongLink"                       // 排除 GNU tar 长文件名头

        );

        // 设置工作目录
        pb.directory(outputPath.toFile());

        // 执行命令
        executeCommand(pb);
    }

    /**
     * 提取空文件结构（仅创建目录和空文件）
     */
    public static void extractEmptyFiles(File archiveFile, File outputDir)
            throws IOException, InterruptedException {

        if (!archiveFile.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND.getCode(), "压缩文件不存在: " + archiveFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(
                "7z", "l", "-slt", archiveFile.getAbsolutePath()
        );

        Process proc = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            String currentPath = null;
            boolean isFolder = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("Path = ")) {
                    currentPath = line.substring(7).trim();
                } else if (line.startsWith("Folder = ")) {
                    isFolder = line.substring(9).trim().equals("+");
                } else if (line.isEmpty() && currentPath != null) {
                    // 安全检查
                    if (isValidFilePath(currentPath)) {
                        try {
                            Path targetPath = outputDir.toPath().resolve(currentPath).normalize();

                            // 确保在输出目录内
                            if (targetPath.startsWith(outputDir.toPath())) {
                                if (isFolder) {
                                    Files.createDirectories(targetPath);
                                } else {
                                    Files.createDirectories(targetPath.getParent());
                                    if (!Files.exists(targetPath)) {
                                        Files.createFile(targetPath);
                                    }
                                }
                            } else {
                                log.warn("跳过不安全的路径: {}", currentPath);
                            }
                        } catch (InvalidPathException e) {
                            log.warn("无效路径: {}", currentPath);
                        }
                    }

                    currentPath = null;
                    isFolder = false;
                }
            }

        }

        boolean finished = proc.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            proc.destroyForcibly();
            throw new IOException("7z 列表命令执行超时");
        }

        int exitCode = proc.exitValue();
        if (exitCode != 0) {
            log.warn("7z 列表命令退出码: {}", exitCode);
        }
    }

    /**
     * 验证文件路径的安全性
     */
    private static boolean isValidFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        // 拒绝包含 .. 的路径
        if (path.contains("..")) {
            log.warn("拒绝包含 '..' 的路径: {}", path);
            return false;
        }

        // 拒绝绝对路径
        if (path.startsWith("/") || path.startsWith("\\") ||
                (path.length() > 1 && path.charAt(1) == ':')) {
            log.debug("拒绝绝对路径: {}", path);
            return false;
        }

        return true;
    }

    /**
     * 解压后的安全验证
     * 删除所有符号链接，防止符号链接攻击
     * 统计文件数量和大小，防止 ZIP 炸弹
     */
    private static void postExtractionValidation(Path outputPath) throws IOException {
        AtomicLong totalSize = new AtomicLong(0);
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicInteger symlinkCount = new AtomicInteger(0);
        AtomicInteger escapeCount = new AtomicInteger(0);
        Path basePath = outputPath.toRealPath();

        Files.walkFileTree(outputPath, new SimpleFileVisitor<>() {
            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                // 检查符号链接
                if (Files.isSymbolicLink(file)) {
                    log.warn("检测到符号链接，删除: {}", file);
                    Files.delete(file);
                    symlinkCount.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                // 检查路径逃逸
                Path realPath = file.toRealPath();
                if (!realPath.startsWith(basePath)) {
                    log.warn("检测到路径逃逸，删除: {}", file);
                    Files.delete(file);
                    escapeCount.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                // 统计
                if (Files.isRegularFile(file)) {
                    long size = Files.size(file);
                    totalSize.addAndGet(size);
                    int count = fileCount.incrementAndGet();

                    // 防止 ZIP 炸弹
                    if (count > MAX_FILE_COUNT) {
                        throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "文件数量超过限制 (" + MAX_FILE_COUNT + ")，疑似 ZIP 炸弹");
                    }

                    if (totalSize.get() > MAX_TOTAL_SIZE) {
                        throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "解压总大小超过限制 (50GB)，疑似 ZIP 炸弹");
                    }

                    // 设置安全权限
                    setSecurePermissions(file);
                }

                return FileVisitResult.CONTINUE;
            }

            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) {
                    log.warn("检测到符号链接目录，删除: {}", dir);
                    Files.delete(dir);
                    symlinkCount.incrementAndGet();
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (symlinkCount.get() > 0) {
            log.warn("⚠️ 检测并删除了 {} 个符号链接", symlinkCount.get());
        }

        if (escapeCount.get() > 0) {
            log.warn("⚠️ 检测并删除了 {} 个路径逃逸文件", escapeCount.get());
        }

        log.info("解压验证完成: {} 个文件, 总大小 {} MB", fileCount.get(), totalSize.get() / 1024 / 1024);
    }

    /**
     * 设置安全的文件权限（不保留原文件权限）
     */
    private static void setSecurePermissions(Path path) {
        try {
            File file = path.toFile();

            boolean success = file.setExecutable(false, false)
                    && file.setWritable(false, false)
                    && file.setReadable(false, false)
                    && file.setWritable(true, true)
                    && file.setReadable(true, true);

            if (!success) {
                log.warn("无法为文件设置标准安全权限: {}", path);
            }
            // 如果是 POSIX 系统，使用更严格的权限
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                try {
                    Files.setPosixFilePermissions(path,
                            Set.of(PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE));
                } catch (UnsupportedOperationException e) {
                    // Windows 系统会抛出异常，忽略
                }
            }
        } catch (Exception e) {
            log.debug("设置文件权限失败: {}", path, e);
        }
    }

    /**
     * 执行命令行工具
     */
    private static void executeCommand(ProcessBuilder pb)
            throws IOException, InterruptedException {

        // 捕获输出用于调试
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 异步读取输出（防止缓冲区满导致死锁）
        Thread outputReader = getOutputReader(process);
        outputReader.start();

        // 等待命令完成
        boolean finished = process.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "7z 命令执行超时（>" + COMMAND_TIMEOUT_MINUTES + " 分钟）");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "7z 解压失败，退出码: " + exitCode);
        }
    }

    private static Thread getOutputReader(Process process) {
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("{} 输出: {}", "7z", line);
                }
            } catch (IOException e) {
                log.debug("读取 {} 输出失败: {}", "7z", e.getMessage());
            }
        }, "7z" + "-output-reader");
        outputReader.setDaemon(true);
        return outputReader;
    }

}
