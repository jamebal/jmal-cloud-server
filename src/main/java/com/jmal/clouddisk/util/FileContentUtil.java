package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
public class FileContentUtil {

    public static String readExcelContent(File file) {
        try (OPCPackage opcPackage = OPCPackage.open(file.getAbsolutePath())) {
            ReadOnlySharedStringsTable readOnlySharedStringsTable = new ReadOnlySharedStringsTable(opcPackage);
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            StringBuilder stringBuilder = new StringBuilder();
            XSSFSheetXMLHandler handler = getXssfSheetXMLHandler(xssfReader, readOnlySharedStringsTable, stringBuilder);
            XMLReader parser = SAXHelper.newXMLReader();
            parser.setContentHandler(handler);

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream stream = sheets.next()) {
                    String sheetName = sheets.getSheetName();
                    stringBuilder.append(sheetName);
                    InputSource sheetSource = new InputSource(stream);
                    parser.parse(sheetSource);
                }
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @NotNull
    private static XSSFSheetXMLHandler getXssfSheetXMLHandler(XSSFReader xssfReader, ReadOnlySharedStringsTable readOnlySharedStringsTable, StringBuilder stringBuilder) throws IOException, InvalidFormatException {
        XSSFSheetXMLHandler.SheetContentsHandler sheetHandler = new XSSFSheetXMLHandler.SheetContentsHandler() {
            @Override
            public void startRow(int rowNum) {
            }

            @Override
            public void endRow(int rowNum) {
            }

            @Override
            public void cell(String cellReference, String formattedValue, XSSFComment comment) {
                // BufferedWriter writer = Files.newBufferedWriter(tempTxtFile, StandardCharsets.UTF_8)
                // writer.write(formattedValue);
                stringBuilder.append(stringBuilder);
            }
        };
        return new XSSFSheetXMLHandler(
                xssfReader.getStylesTable(), null, readOnlySharedStringsTable, sheetHandler, new DataFormatter(), false);
    }

    public static String readPdfContent(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            return pdfStripper.getText(document);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static void main(String[] args) {
        File file = new File("/Users/jmal/temp/filetest/rootpath/jmal/移动数据中心机房能源管理大数据平台功能建议-zhouguang-2023.4.17.docx");
        System.out.println(readWordContent(file));
    }
}
