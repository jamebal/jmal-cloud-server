package com.jmal.clouddisk.util;

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
import org.apache.commons.compress.utils.IOUtils;
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

    /**
     * 压缩文件夹下的指定文件到指定zip文件
     *
     * @param paths      源文件列表
     * @param targetFile 目标指定zip文件
     * @throws IOException IO异常，抛出给调用者处理
     */
    public static void compress(List<Path> paths, String targetFile) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
            compress(paths, outputStream);
        }
    }

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
     * 解压zip文件到指定文件夹
     *
     * @param zipFileName 源zip文件路径
     * @param destDir     解压后输出路径
     * @param isWrite     是否输出文件
     * @throws IOException IO异常，抛出给调用者处理
     */
    public static void unzip(String zipFileName, String destDir, boolean isWrite) throws IOException {
        unZip(new File(zipFileName), destDir, isWrite);
    }

    /**
     * 解压 .zip 文件
     *
     * @param file      要解压的zip文件对象
     * @param outputDir 要解压到某个指定的目录下
     * @param isWrite   是否输出文件
     * @throws IOException
     */
    public static void unZip(File file, String outputDir, boolean isWrite) throws IOException {
        ZipFile zipFile = new ZipFile(file, "utf-8");
        //创建输出目录
        createDirectory(outputDir, null);
        Enumeration<?> enums = zipFile.getEntries();
        while (enums.hasMoreElements()) {
            org.apache.tools.zip.ZipEntry entry = (ZipEntry) enums.nextElement();
            InputStream in = zipFile.getInputStream(entry);
            if (entry.isDirectory()) {
                //创建空目录
                createDirectory(outputDir, entry.getName());
            } else {
                Path parentPath = Paths.get(outputDir, entry.getName()).getParent();
                if (!Files.exists(parentPath)) {
                    parentPath.toFile().mkdirs();
                }
                OutputStream out = new FileOutputStream(new File(outputDir + File.separator + entry.getName()));
                if (isWrite) {
                    IOUtils.copy(in, out);
                }
            }
        }
        zipFile.close();
    }

    public static void decompressTarGz(File file, String outputDir, boolean isWrite) throws IOException {
        TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file))));
        //创建输出目录
        createDirectory(outputDir, null);
        TarArchiveEntry entry = null;
        while ((entry = tarIn.getNextTarEntry()) != null) {
            decompress(outputDir, isWrite, tarIn, entry);
        }
    }

    public static void decompressRAR(File file, String outputDir, boolean isWrite) throws IOException {

    }

    public static void decompressJar(File file, String outputDir, boolean isWrite) throws IOException {
        JarArchiveInputStream inputStream = new JarArchiveInputStream(new FileInputStream(file));
        //创建输出目录
        createDirectory(outputDir, null);
        JarArchiveEntry entry = null;
        while (Objects.nonNull(entry = inputStream.getNextJarEntry())) {
            decompress(outputDir, isWrite, inputStream, entry);
        }
    }

    public static void decompressTar(File file, String outputDir, boolean isWrite) throws IOException {
        TarArchiveInputStream inputStream = new TarArchiveInputStream(new FileInputStream(file));
        //创建输出目录
        createDirectory(outputDir, null);
        TarArchiveEntry entry = null;
        while (Objects.nonNull(entry = inputStream.getNextTarEntry())) {
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
        while (Objects.nonNull(entry = tarIn.getNextTarEntry())) {
            decompress(outputDir, isWrite, tarIn, entry);
        }
    }

    private static void decompress(String outputDir, boolean isWrite, InputStream inputStream, ArchiveEntry entry) throws IOException {
        //是目录
        if (entry.isDirectory()) {
            //创建空目录
            createDirectory(outputDir, entry.getName());
        } else {
            Path parentPath = Paths.get(outputDir, entry.getName()).getParent();
            if (!Files.exists(parentPath)) {
                parentPath.toFile().mkdirs();
            }
            OutputStream out = new FileOutputStream(new File(outputDir + File.separator + entry.getName()));
            if (isWrite) {
                IOUtils.copy(inputStream, out);
            }
        }
    }

    /**
     * 创建目录
     *
     * @param outputDir
     * @param subDir
     */
    public static void createDirectory(String outputDir, String subDir) {
        File file = new File(outputDir);
        //子目录不为空
        if (!(subDir == null || subDir.trim().equals(""))) {
            file = new File(outputDir + File.separator + subDir);
        }
        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.mkdirs();
        }
    }

    public static void main(String[] args) throws IOException {

        List<Path> paths = List.of(
                Paths.get("/Users/jmal/temp/filetest/rootpath/jmal/滨滨广场点位表.xlsx"),
                Paths.get("/Users/jmal/temp/filetest/rootpath/jmal/测试移动/IMG_0465.MOV"),
                Paths.get("/Users/jmal/Downloads/demo")
        );

        compress(paths, "/Users/jmal/Downloads/压缩测试.zip");
    }

}
