package com.jmal.clouddisk.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.apache.tools.zip.ZipOutputStream;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

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
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                addToZip(rootDir, file, zipOut);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
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

    public static void decompress(String filePath, String outputDir, boolean isWrite) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        try {
            if (filePath.endsWith(".zip")) {
                unZip(file, outputDir, isWrite);
            } else if (filePath.endsWith(".tar")) {
                decompressTar(file, outputDir, isWrite);
            } else if (filePath.endsWith(".7z")) {
                decompressSevenZ(file, outputDir, isWrite);
            } else if (filePath.endsWith(".rar")) {
                decompressRar(file, outputDir, isWrite);
            } else if (filePath.endsWith(".jar")) {
                decompressJar(file, outputDir, isWrite);
            } else if (filePath.endsWith(".tar.gz") || filePath.endsWith(".tgz") || filePath.endsWith(".gz")) {
                decompressTarGz(file, outputDir, isWrite);
            } else if (filePath.endsWith(".tar.bz2")) {
                decompressTarBz2(file, outputDir, isWrite);
            } else {
                throw new CommonException(ExceptionType.UNRECOGNIZED_FILE);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new CommonException(ExceptionType.FAIL_DECOMPRESS);
        }
    }

    /**
     * 解压 .zip 文件
     *
     * @param file      要解压的zip文件对象
     * @param outputDir 要解压到某个指定的目录下
     * @param isWrite   是否输出文件
     */
    public static void unZip(File file, String outputDir, boolean isWrite) throws IOException {
        ZipFile zipFile = new ZipFile(file, "utf-8");
        //创建输出目录
        createDirectory(outputDir, null);
        Enumeration<?> enums = zipFile.getEntries();
        while (enums.hasMoreElements()) {
            org.apache.tools.zip.ZipEntry entry = (ZipEntry) enums.nextElement();
            InputStream in = zipFile.getInputStream(entry);
            decompress(outputDir, isWrite, in, entry.isDirectory(), entry.getName());
        }
        zipFile.close();
    }

    private static void decompress(String outputDir, boolean isWrite, InputStream in, boolean directory, String name) throws FileNotFoundException {
        if (directory) {
            //创建空目录
            createDirectory(outputDir, name);
        } else {
            Path parentPath = Paths.get(outputDir, name).getParent();
            if (!Files.exists(parentPath)) {
                FileUtil.mkdir(parentPath.toFile());
            }
            OutputStream out = new FileOutputStream(outputDir + File.separator + name);
            if (isWrite) {
                IoUtil.copy(in, out);
            }
        }
    }

    public static void decompressTarGz(File file, String outputDir, boolean isWrite) throws IOException {
        TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file))));
        //创建输出目录
        createDirectory(outputDir, null);
        TarArchiveEntry entry;
        while ((entry = tarIn.getNextEntry()) != null) {
            decompress(outputDir, isWrite, tarIn, entry);
        }
    }

    public static void decompressJar(File file, String outputDir, boolean isWrite) throws IOException {
        JarArchiveInputStream inputStream = new JarArchiveInputStream(new FileInputStream(file));
        // 创建输出目录
        createDirectory(outputDir, null);
        JarArchiveEntry entry;
        while (Objects.nonNull(entry = inputStream.getNextEntry())) {
            decompress(outputDir, isWrite, inputStream, entry);
        }
    }

    private static void decompressSevenZ(File sevenZFile, String outputDir, boolean isWrite) throws IOException {
        // 创建输出目录
        createDirectory(outputDir, null);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("7z", "x", sevenZFile.getAbsolutePath(), "-o" + outputDir, "-y");
        // 将输出和错误流重定向到空输出流
        executingCommand(processBuilder, "7z");
    }

    private static void decompressRar(File sevenZFile, String outputDir, boolean isWrite) throws IOException {
        // 创建输出目录
        createDirectory(outputDir, null);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("unrar", "x", "-o+", sevenZFile.getAbsolutePath(), outputDir);
        executingCommand(processBuilder, "rar");
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

    public static void decompressTar(File file, String outputDir, boolean isWrite) throws IOException {
        TarArchiveInputStream inputStream = new TarArchiveInputStream(new FileInputStream(file));
        //创建输出目录
        createDirectory(outputDir, null);
        TarArchiveEntry entry;
        while (Objects.nonNull(entry = inputStream.getNextEntry())) {
            decompress(outputDir, isWrite, inputStream, entry);
        }
    }

    /**
     * 解压缩tar.bz2文件
     *
     * @param file      压缩包文件
     * @param outputDir 目标文件夹
     */
    public static void decompressTarBz2(File file, String outputDir, boolean isWrite) throws IOException {
        TarArchiveInputStream tarIn = new TarArchiveInputStream(new BZip2CompressorInputStream(new FileInputStream(file)));
        createDirectory(outputDir, null);
        TarArchiveEntry entry;
        while (Objects.nonNull(entry = tarIn.getNextEntry())) {
            decompress(outputDir, isWrite, tarIn, entry);
        }
    }

    private static void decompress(String outputDir, boolean isWrite, InputStream inputStream, ArchiveEntry entry) throws IOException {
        //是目录
        decompress(outputDir, isWrite, inputStream, entry.isDirectory(), entry.getName());
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
