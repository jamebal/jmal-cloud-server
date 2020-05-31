package com.jmal.clouddisk.util;

import com.jmal.clouddisk.model.FileDocument;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
     * @param fileDocuments 源文件夹
     * @param targetFile    目标知道zip文件
     * @throws IOException IO异常，抛出给调用者处理
     */
    public static void zip(String startPath, List<FileDocument> fileDocuments, String targetFile) throws IOException {

        try (
                OutputStream outputStream = new FileOutputStream(targetFile)
        ) {
            zip(startPath, fileDocuments, outputStream);
        }
    }

    /**
     * 压缩文件夹到指定输出流中，可以是本地文件输出流，也可以是web响应下载流
     *
     * @param fileDocuments fileDocuments       源文件夹
     * @param outputStream  压缩后文件的输出流
     * @throws IOException IO异常，抛出给调用者处理
     */
    private static void zip(String startPath, List<FileDocument> fileDocuments, OutputStream outputStream) throws IOException {
        try (
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                ArchiveOutputStream out = new ZipArchiveOutputStream(bufferedOutputStream)
        ) {
            for (FileDocument fileDocument : fileDocuments) {
                String pathname = startPath + fileDocument.getName();
                if (fileDocument.getIsFolder()) {
                    String parentPath = fileDocument.getName() + File.separator;
                    zipSingle(parentPath, pathname, out);
                } else {
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
     * @param isWrite     是否输出文件
     * @throws IOException IO异常，抛出给调用者处理
     */
    public static void unzip(String zipFileName, String destDir, boolean isWrite) throws IOException {
        try (
                InputStream inputStream = new FileInputStream(zipFileName);
        ) {
            unzip(inputStream, destDir, isWrite);
        }

    }

    /**
     * 从输入流中获取zip文件，并解压到指定文件夹
     *
     * @param inputStream zip文件输入流，可以是本地文件输入流，也可以是web请求上传流
     * @param destDir     解压后输出路径
     * @param isWrite     是否输出文件
     * @throws IOException IO异常，抛出给调用者处理
     */
    private static void unzip(InputStream inputStream, String destDir, boolean isWrite) throws IOException {
        try (
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                ArchiveInputStream in = new ZipArchiveInputStream(bufferedInputStream);
        ) {
            ArchiveEntry entry;
            if(Objects.nonNull(entry = in.getNextEntry())){
                if (in.canReadEntryData(entry)) {
                    File firstFile = Paths.get(destDir, entry.getName()).toFile();
                    File path = new File(firstFile.getParent());
                    if(!path.exists()){
                        path.mkdirs();
                    }
                    writeFile(isWrite, in, entry, firstFile);
                } else {
                    System.out.println(entry.getName());
                }
            }
            int i = 0;
            while (Objects.nonNull(entry = in.getNextEntry())) {
                if(i > 100){
                    break;
                }else{
                    if (in.canReadEntryData(entry)) {
                        File file = Paths.get(destDir, entry.getName()).toFile();
                        writeFile(isWrite, in, entry, file);
                    } else {
                        System.out.println(entry.getName());
                    }
                }
                i++;
            }
        }

    }

    private static void writeFile(boolean isWrite, ArchiveInputStream in, ArchiveEntry entry, File file) throws IOException {
        System.out.println(file.getPath());
        if (entry.isDirectory()) {
            if (!file.exists()) {
                file.mkdirs();
            }
        } else {
            try (
                    OutputStream out = new FileOutputStream(file);
            ) {
                if (isWrite) {
                    IOUtils.copy(in, out);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        long stime = System.currentTimeMillis();
        String filePath = "/Users/jmal/Downloads/归档.zip";
        String destDir = "/Users/jmal/Downloads/归档";
        CompressUtils.unzip(filePath, destDir, false);
        System.out.println(System.currentTimeMillis() - stime);
    }

}
