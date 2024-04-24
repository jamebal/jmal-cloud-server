package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
public class FileContentUtil {

    public static String readPdfContent(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            return pdfStripper.getText(document);
        } catch (IOException e) {
            log.warn("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage(), e);
        }
        return null;
    }

    public static String readPPTContent(File file) {
        try (FileInputStream fis = new FileInputStream(file.getAbsolutePath());
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            StringBuilder stringBuilder = new StringBuilder();
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        stringBuilder.append(textShape.getText());
                    }
                }
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            log.warn("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage(), e);
        }
        return null;
    }

    public static String readWordContent(File file) {
        try (FileInputStream fis = new FileInputStream(file.getAbsolutePath());
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder stringBuilder = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph para : paragraphs) {
                stringBuilder.append(para.getText());
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            log.warn("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage(), e);
        }
        return null;
    }

}
