package com.jmal.clouddisk;

import cn.hutool.core.date.TimeInterval;
import com.itextpdf.styledxmlparser.jsoup.Jsoup;
import com.itextpdf.styledxmlparser.jsoup.nodes.Element;
import com.itextpdf.styledxmlparser.jsoup.select.Elements;
import com.spire.doc.Document;
import com.spire.doc.FileFormat;
import com.spire.doc.documents.ImageType;
import fr.opensagres.poi.xwpf.converter.xhtml.XHTMLConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;

@Slf4j
public class WordCoverTest {
    public static void main(String[] args) {
        try {
            TimeInterval timeInterval = new TimeInterval();
            File pdfFile = new File("/Users/jmal/Downloads/未命名文件1-2.docx");
            File coverImageFile = wordCoverImage(pdfFile);
            System.out.println("word 提取封面图像耗时: " + timeInterval.intervalMs() + "ms");
            System.out.println("word 封面图像已保存为: " + coverImageFile);
            timeInterval.restart();
            String html = docxToHtml(pdfFile);
            System.out.println("word 转换为html: " + html);
            System.out.println("word 转换为html耗时: " + timeInterval.intervalMs() + "ms");
        } catch (IOException e) {
            System.out.println("word 提取封面图像失败: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static File wordCoverImage(File wordFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(wordFile.getAbsolutePath())) {
            Document document = new Document();
            document.loadFromStream(fis, FileFormat.Docx);
            if (document.getPageCount() > 0) {
                // 获取第1页并转换为图片
                BufferedImage image = document.saveToImages(0, ImageType.Bitmap);
                // 将图片写入输出流
                // ByteArrayOutputStream imageOutputStream = new ByteArrayOutputStream();
                // ImageIO.write(image, "png", imageOutputStream);
                // 保存图片
                File coverImageFile = new File(wordFile.getParent(), wordFile.getName().replace(".docx", "_cover.png"));
                ImageIO.write(image, "png", coverImageFile);
                return coverImageFile;
            }
        }
        return null;
    }

    public static String docxToHtml(File wordFile) throws Exception {
        InputStream input = new FileInputStream(wordFile);
        XWPFDocument document = new XWPFDocument(input);
        List<XWPFPictureData> list = document.getAllPictures();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XHTMLConverter.getInstance().convert(document, outputStream, null);
        String s = outputStream.toString();
        return setImg(s, list);
    }

    private static String setImg(String html, List<XWPFPictureData> list) {
        com.itextpdf.styledxmlparser.jsoup.nodes.Document doc = Jsoup.parse(html);
        Elements elements = doc.getElementsByTag("img");
        if (elements != null && !elements.isEmpty() && list != null) {
            for (Element element : elements) {
                String src = element.attr("src");
                for (XWPFPictureData data : list) {
                    if (src.contains(data.getFileName())) {
                        String type = src.substring(src.lastIndexOf(".") + 1);
                        String base64 = "data:image/" + type + ";base64," + new String(Base64.encodeBase64(data.getData()));
                        element.attr("src", base64);
                        break;
                    }
                }
            }
        }
        return doc.toString();
    }

}
