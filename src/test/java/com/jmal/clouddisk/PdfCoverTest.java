package com.jmal.clouddisk;

import cn.hutool.core.date.TimeInterval;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.exception.TikaException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PdfCoverTest {
    public static void main(String[] args) {
        try {
            TimeInterval timeInterval = new TimeInterval();
            File pdfFile = new File("/Users/jmal/Downloads/1001241328 JLG BIM Content User Guide - Boom Lift (1).pdf");
            File coverImageFile = pdfCoverImage(pdfFile);
            System.out.println("pdf 提取封面图像耗时: " + timeInterval.intervalMs() + "ms");
            System.out.println("pdf 封面图像已保存为: " + coverImageFile);
        } catch (IOException | TikaException e) {
            System.out.println("pdf 提取封面图像失败: " + e.getMessage());
        }
    }

    private static File pdfCoverImage(File pdfFile) throws IOException, TikaException {
        // 使用PDFBox读取PDF并提取封面图像
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(pdfFile))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            BufferedImage coverImage = pdfRenderer.renderImageWithDPI(0, 128, ImageType.RGB);

            // 将封面图像保存为JPEG文件
            File coverImageFile = new File(pdfFile.getParent(), pdfFile.getName().replace(".pdf", "_cover.jpg"));
            ImageIO.write(coverImage, "JPEG", coverImageFile);
            return coverImageFile;
        }
    }
}
