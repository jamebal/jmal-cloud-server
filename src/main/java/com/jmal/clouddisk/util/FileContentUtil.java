package com.jmal.clouddisk.util;

import com.jmal.clouddisk.lucene.PopplerPdfReader;
import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileContentUtil {

    public static void readFailed(File file, IOException e) {
        log.warn("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage());
    }

    public static File getCoverPath(String outputPath) {
        return new File(outputPath, "cover.jpg");
    }

    public static File pdfCoverImage(File pdfFile, String outputDir) {
        try {
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);
            Path coverPath = outputPath.resolve("cover.png");
            PopplerPdfReader.executeCommand("pdftoppm", "-png", "-f", "1", "-l", "1", "-singlefile", pdfFile.getAbsolutePath(), coverPath.toString().replace(".png", ""));
            return coverPath.toFile();
        } catch (Throwable e) {
            log.warn("PDF 文件封面图像生成失败: {}, file: {}", e.getMessage(), pdfFile.getName());
            return null;
        }
    }

    public static File epubCoverImage(File file, Book book, String outputPath) {
        // 获取封面图片
        Resource coverImage = book.getCoverImage();
        if (coverImage != null) {
            File coverImageFile = getCoverPath(outputPath);
            try (InputStream coverImageInputStream = coverImage.getInputStream();
                 OutputStream coverImageOutput = new FileOutputStream(coverImageFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = coverImageInputStream.read(buffer)) != -1) {
                    coverImageOutput.write(buffer, 0, bytesRead);
                }
                return coverImageFile;
            } catch (Throwable e) {
                log.warn("epub 文件封面图像生成失败: {}, 文件: {}" ,e.getMessage(), file.getAbsoluteFile());
                return null;
            }
        }
        log.warn("epub 文件封面图像生成失败: 未找到封面图片, 文件: {}", file.getAbsoluteFile());
        return null;
    }

}
