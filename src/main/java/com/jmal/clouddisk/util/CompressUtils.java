package com.jmal.clouddisk.util;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import lombok.extern.slf4j.Slf4j;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * 压缩文件夹工具类
 *
 * @author jmal
 */
@Slf4j
public class CompressUtils {

    public static void compress(List<Path> paths, OutputStream outputStream) {
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            // 遍历每个路径
            // 遍历每个路径并处理
            for (Path path : paths) {
                if (Files.isDirectory(path)) {
                    // 处理文件夹及其内容
                    addDirectoryToZip(path.getParent(), path, zipOut);
                } else {
                    // 直接处理文件
                    addToZip(path.getParent(), path, zipOut);
                }
            }
        } catch (IOException e) {
            log.warn("压缩文件失败", e);
        }
    }

    private static void addDirectoryToZip(Path rootDir, Path sourceDir, ZipOutputStream zipOut) throws IOException {
        // 遍历文件夹及其子文件夹内容
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                addToZip(rootDir, file, zipOut);
                return FileVisitResult.CONTINUE;
            }

            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(sourceDir)) {
                    addToZip(rootDir, dir, zipOut);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void addToZip(Path rootDir, Path file, ZipOutputStream zipOut) throws IOException {
        String zipEntryName = rootDir.relativize(file).toString().replace("\\", "/");
        if (Files.isDirectory(file)) {
            zipEntryName += "/";
        }
        ZipEntry zipEntry = new ZipEntry(zipEntryName);
        zipOut.putNextEntry(zipEntry);
        if (!Files.isDirectory(file)) {
            Files.copy(file, zipOut);
        }
        zipOut.closeEntry();
    }

    public static void decompress(String filePath, String outputDir, boolean isWrite) throws IOException, InterruptedException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        String compressionType = DetectArchiveType.detectType(filePath);
        if (!isWrite) {
            // 创建输出目录
            extractEmptyFiles(file, new File(outputDir));
            return;
        }
        switch (compressionType) {
            case "jar", "7z", "zip", "rar5", "rar", "tar":
                decompressSevenZ(file, outputDir);
                break;
            case "gzip":
                decompressGzip(file, outputDir);
                break;
            case "bzip2":
                decompressBzip2(file, outputDir);
                break;
            default:
                throw new CommonException(ExceptionType.UNRECOGNIZED_FILE);
        }
    }

    public static void extractEmptyFiles(File archiveFile, File outputDir) throws IOException, InterruptedException {
        Process proc = Runtime.getRuntime().exec(new String[]{"7z", "l", archiveFile.getAbsolutePath()});
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        boolean inFileSection = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("----------")) {
                inFileSection = !inFileSection;
                continue;
            }
            if (inFileSection && !line.trim().isEmpty()) {
                // 7z l 输出格式，第6列是文件名
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 6) {
                    String filePath = parts[5];
                    // 目录或文件
                    File target = new File(outputDir, filePath);
                    if (filePath.endsWith("/") || filePath.endsWith("\\")) {
                        FileUtil.mkdir(target);
                    } else {
                        File parent = target.getParentFile();
                        if (parent != null) {
                            FileUtil.mkdir(parent);
                        }
                        FileUtil.newFile(filePath);
                    }
                }
            }
        }
        reader.close();
        proc.waitFor();
    }

    private static void decompressSevenZ(File sevenZFile, String outputDir) throws IOException {
        // 创建输出目录
        createDirectory(outputDir, null);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("7z", "x", sevenZFile.getAbsolutePath(), "-o" + outputDir, "-y");
        // 将输出和错误流重定向到空输出流
        executingCommand(processBuilder, "7z");
    }

    private static void decompressGzip(File gzipFile, String outputDir) throws IOException {
        // 创建输出目录
        createDirectory(outputDir, null);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("tar", "-xzf", gzipFile.getAbsolutePath(), "-C" + outputDir);
        // 将输出和错误流重定向到空输出流
        executingCommand(processBuilder, "gzip");
    }

    private static void decompressBzip2(File bzip2File, String outputDir) throws IOException {
        // 创建输出目录
        createDirectory(outputDir, null);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("tar", "-jxf", bzip2File.getAbsolutePath(), "-C" + outputDir);
        // 将输出和错误流重定向到空输出流
        executingCommand(processBuilder, "bzip2");
    }

    private static void executingCommand(ProcessBuilder processBuilder, String type) throws IOException {
        // 将输出和错误流重定向到空输出流
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to extract " + type + " file. Exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Extraction interrupted", e);
        }
    }

    /**
     * 创建目录
     *
     * @param outputDir 输出目录
     * @param subDir   子目录
     */
    public static void createDirectory(String outputDir, String subDir) {
        File file = new File(outputDir);
        //子目录不为空
        if (!(subDir == null || subDir.trim().isEmpty())) {
            file = new File(outputDir + File.separator + subDir);
        }
        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                FileUtil.mkdir(file.getParentFile());
            }
            FileUtil.mkdir(file);
        }
    }

}
