package com.jmal.clouddisk.util;

import com.jmal.clouddisk.model.FileDocument;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 压缩文件夹工具类
 *
 * @blame jmal
 */
public class CompressUtils {

    /**
     * 压缩文件夹到指定zip文件
     *
     * @param fileDocuments     源文件夹
     * @param targetFile 目标知道zip文件
     * @throws IOException IO异常，抛出给调用者处理
     */
    public static void zip(String startPath ,List<FileDocument> fileDocuments, String targetFile) throws IOException {

        try (
                OutputStream outputStream = new FileOutputStream(targetFile)
        ) {
            zip(startPath, fileDocuments, outputStream);
        }
    }

    public static void main(String[] args) throws IOException {

        String startPath = "/Users/jmal/temp/filetest/rootpath/jmal/图片/Pictures/";

        long s = System.currentTimeMillis();
        List<FileDocument> fileDocuments = new ArrayList<>();
        FileDocument fileDocument1 = new FileDocument();
        fileDocument1.setIsFolder(true);
        fileDocument1.setName("截图");
        fileDocuments.add(fileDocument1);

        FileDocument fileDocument2 = new FileDocument();
        fileDocument2.setIsFolder(true);
        fileDocument2.setName("pap.er");
        fileDocuments.add(fileDocument2);

        FileDocument fileDocument3 = new FileDocument();
        fileDocument3.setIsFolder(false);
        fileDocument3.setName("test.jpg");
        fileDocuments.add(fileDocument3);

        zip(startPath, fileDocuments, startPath + "download.zip");
        System.out.println(System.currentTimeMillis() - s);
    }

    /**
     * 压缩文件夹到指定输出流中，可以是本地文件输出流，也可以是web响应下载流
     *
     * @param fileDocuments fileDocuments       源文件夹
     * @param outputStream 压缩后文件的输出流
     * @throws IOException IO异常，抛出给调用者处理
     */
    private static void zip(String  startPath,List<FileDocument> fileDocuments, OutputStream outputStream) throws IOException {
        try (
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                ArchiveOutputStream out = new ZipArchiveOutputStream(bufferedOutputStream)
        ) {
            for (FileDocument fileDocument : fileDocuments) {
                String pathname = startPath + fileDocument.getName();
                if(fileDocument.getIsFolder()){
                    String parentPath = fileDocument.getName() + File.separator;
                    zipSingle(parentPath, pathname, out);
                }else{
                    File file = new File(pathname);
                    try (
                            InputStream input = new FileInputStream(file)
                    ) {
                        ArchiveEntry entry = new ZipArchiveEntry(file, fileDocument.getName());
                        out.putArchiveEntry(entry);
                        IOUtils.copy(input, out);
                        out.closeArchiveEntry();
                    }
                }
            }
        }
    }

    private static void zipSingle(String parentPath, String srcDir, ArchiveOutputStream out) throws IOException {
        Path start = Paths.get(srcDir);
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String entryName = parentPath + start.relativize(dir).toString();
                ArchiveEntry entry = new ZipArchiveEntry(dir.toFile(), entryName);
                out.putArchiveEntry(entry);
                out.closeArchiveEntry();
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try (
                        InputStream input = new FileInputStream(file.toFile())
                ) {
                    String entryName = parentPath + start.relativize(file).toString();
                    ArchiveEntry entry = new ZipArchiveEntry(file.toFile(), entryName);
                    out.putArchiveEntry(entry);
                    IOUtils.copy(input, out);
                    out.closeArchiveEntry();
                }
                return super.visitFile(file, attrs);
            }

        });
    }

    /**
     * 解压zip文件到指定文件夹
     *
     * @param zipFileName 源zip文件路径
     * @param destDir     解压后输出路径
     * @throws IOException IO异常，抛出给调用者处理
     */
    public static void unzip(String zipFileName, String destDir) throws IOException {
        try (
                InputStream inputStream = new FileInputStream(zipFileName);
        ) {
            unzip(inputStream, destDir);
        }

    }

    /**
     * 从输入流中获取zip文件，并解压到指定文件夹
     *
     * @param inputStream zip文件输入流，可以是本地文件输入流，也可以是web请求上传流
     * @param destDir     解压后输出路径
     * @throws IOException IO异常，抛出给调用者处理
     */
    private static void unzip(InputStream inputStream, String destDir) throws IOException {
        try (
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                ArchiveInputStream in = new ZipArchiveInputStream(bufferedInputStream);
        ) {
            ArchiveEntry entry;
            while (Objects.nonNull(entry = in.getNextEntry())) {
                if (in.canReadEntryData(entry)) {
                    File file = Paths.get(destDir, entry.getName()).toFile();
                    if (entry.isDirectory()) {
                        if (!file.exists()) {
                            file.mkdirs();
                        }
                    } else {
                        try (
                                OutputStream out = new FileOutputStream(file);
                        ) {
                            IOUtils.copy(in, out);
                        }
                    }
                } else {
                    System.out.println(entry.getName());
                }
            }
        }

    }

    private static String getUserDirectory(String currentDirectory) {
        String mongodbEndPath = "/";
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = "/";
        } else {
            if (!currentDirectory.endsWith(mongodbEndPath)) {
                currentDirectory += "/";
            }
        }
        currentDirectory = currentDirectory.replaceAll("/", File.separator);
        return currentDirectory;
    }

}
