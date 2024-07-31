package com.jmal.clouddisk;

import cn.hutool.core.date.TimeInterval;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.epub.EpubReader;
import org.apache.tika.exception.TikaException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class EpubCoverTest {
    public static void main(String[] args) {
        try {
            TimeInterval timeInterval = new TimeInterval();
            File epubFile = new File("/Users/jmal/Downloads/《延展力》未来职场的创造性重构与自我迭代.epub");
            File coverImageFile = epubCoverImage(epubFile);
            System.out.println("epub 提取封面图像耗时: " + timeInterval.intervalMs() + "ms");
            System.out.println("epub 封面图像已保存为: " + coverImageFile);

            // 读取 EPUB 内容
            String content = readEpubContent(epubFile);
            System.out.println("epub 内容: " + content);
        } catch (IOException | TikaException e) {
            System.out.println("epub 提取封面图像失败: " + e.getMessage());
        }
    }

    private static File epubCoverImage(File epubFile) throws IOException, TikaException {
        try {
            // 打开 EPUB 文件
            FileInputStream fileInputStream = new FileInputStream(epubFile);
            EpubReader epubReader = new EpubReader();
            Book book = epubReader.readEpub(fileInputStream);

            // 获取封面图片
            Resource coverImage = book.getCoverImage();
            if (coverImage != null) {
                InputStream coverImageInputStream = coverImage.getInputStream();
                File coverImageFile = Paths.get(epubFile.getParent(), epubFile.getName().replace(".epub", "_cover.jpg")).toFile();
                OutputStream coverImageOutput = new FileOutputStream(coverImageFile);

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = coverImageInputStream.read(buffer)) != -1) {
                    coverImageOutput.write(buffer, 0, bytesRead);
                }
                coverImageInputStream.close();
                coverImageOutput.close();
                return coverImageFile;
            } else {
                System.out.println("EPUB 文件中没有封面");
            }
            fileInputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String readEpubContent(File epubFile) {
        try {
            // 打开 EPUB 文件
            InputStream fileInputStream = new FileInputStream(epubFile);
            EpubReader epubReader = new EpubReader();
            Book book = epubReader.readEpub(fileInputStream);
            StringBuilder content = new StringBuilder();
            // 获取章节内容
            Spine spine = book.getSpine();
            for (int i = 0; i < spine.size(); i++) {
                Resource resource = spine.getResource(i);
                InputStream is = resource.getInputStream();
                byte[] bytes = is.readAllBytes();
                String htmlContent = new String(bytes, StandardCharsets.UTF_8);
                // 使用 JSoup 解析 HTML 并提取纯文本
                Document document = Jsoup.parse(htmlContent);
                String textContent = document.text();
                content.append(textContent);
                is.close();
            }
            fileInputStream.close();
            return content.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
