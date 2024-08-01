package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

@Slf4j
public class FileContentUtil {

    public static void readFailed(File file, IOException e) {
        log.warn("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage());
    }

    public static File getCoverPath(String outputPath) {
        return new File(outputPath, "cover.jpg");
    }

    public static File pdfCoverImage(File file, PDDocument document, String outputPath) {
        try {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            BufferedImage coverImage = pdfRenderer.renderImageWithDPI(0, 128, ImageType.RGB);
            // 将封面图像保存为JPEG文件
            File coverImageFile = getCoverPath(outputPath);
            ImageIO.write(coverImage, "JPEG", coverImageFile);
            return coverImageFile;
        } catch (Throwable e) {
            log.warn("PDF 文件封面图像生成失败: {}, file: {}", e.getMessage(), file.getName());
            return null;
        }
    }

    public static File epubCoverImage(Book book, String outputPath) {
        // 获取封面图片
        Resource coverImage = book.getCoverImage();
        if (coverImage != null) {
            File coverImageFile = getCoverPath(outputPath);
            try (InputStream coverImageInputStream = coverImage.getInputStream();
                 OutputStream coverImageOutput = new FileOutputStream(coverImageFile);) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = coverImageInputStream.read(buffer)) != -1) {
                    coverImageOutput.write(buffer, 0, bytesRead);
                }
                return coverImageFile;
            } catch (Throwable e) {
                log.warn("epub 文件封面图像生成失败: {}", e.getMessage());
                return null;
            }
        }
        log.warn("epub 文件封面图像生成失败: 未找到封面图片");
        return null;
    }

}
