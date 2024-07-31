package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Slf4j
public class FileContentUtil {

    public static void readFailed(File file, IOException e) {
        log.warn("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage());
    }

    public static File pdfCoverImage(PDDocument document, String outputPath) {
        try {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageIndex = 0;
            int dpi = 150;

            // 渲染第一页的图像，获取图像的尺寸
            BufferedImage tempImage = pdfRenderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            int width = tempImage.getWidth();
            int height = tempImage.getHeight();
            tempImage.flush();

            // 创建用于逐块渲染的BufferedImage
            BufferedImage coverImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = coverImage.createGraphics();

            // 设置渲染块的大小
            int blockSize = 100;
            for (int y = 0; y < height; y += blockSize) {
                BufferedImage blockImage = pdfRenderer.renderImage(pageIndex);
                graphics.drawImage(blockImage, 0, y, null);
                blockImage.flush();
            }
            graphics.dispose();

            // 将封面图像保存为JPEG文件
            File coverImageFile = new File(outputPath, "cover.jpg");
            ImageIO.write(coverImage, "JPEG", coverImageFile);

            coverImage.flush();
            return coverImageFile;
        } catch (Exception e) {
            return null;
        }
    }

}
