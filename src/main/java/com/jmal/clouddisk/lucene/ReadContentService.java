package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.ocr.OcrService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.FileContentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.epub.EpubReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadContentService {

    private final OcrService ocrService;

    public final CommonFileService commonFileService;

    public final TaskProgressService taskProgressService;

    public final VideoProcessService videoProcessService;

    /**
     * 将 DWG 文件转换为 MXWeb 文件
     *
     * @param file   文件
     * @param fileId 文件 ID
     * @return MXWeb 文件路径
     */
    public String dwg2mxweb(File file, String fileId) {
        String username = commonFileService.getUsernameByAbsolutePath(Path.of(file.getAbsolutePath()));
        // 生成封面图像
        if (StrUtil.isNotBlank(fileId)) {
            String outputName = file.getName() + Constants.MXWEB_SUFFIX;
            FileContentUtil.dwgConvert(file.getAbsolutePath(), videoProcessService.getVideoCacheDir(username, fileId), outputName);
        }
        return null;
    }

    public String readPdfContent(File file, String fileId) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(file))) {

            String username = commonFileService.getUsernameByAbsolutePath(Path.of(file.getAbsolutePath()));

            // 生成封面图像
            if (StrUtil.isNotBlank(fileId)) {
                File coverFile = FileContentUtil.pdfCoverImage(file, document, videoProcessService.getVideoCacheDir(username, fileId));
                commonFileService.updateCoverFileDocument(fileId, coverFile);
            }

            StringBuilder content = new StringBuilder();
            // 提取每一页的内容
            PDFTextStripper pdfStripper = new PDFTextStripper();
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                pdfStripper.setStartPage(pageNumber);
                pdfStripper.setEndPage(pageNumber);
                String text = pdfStripper.getText(document).trim();
                if (!text.isEmpty()) {
                    content.append(text);
                } else {
                    taskProgressService.addTaskProgress(file, TaskType.OCR, pageNumber + "/" + document.getNumberOfPages());
                    PDPage page = document.getPage(pageNumber - 1);
                    PDResources resources = page.getResources();
                    for (COSName xObjectName : resources.getXObjectNames()) {
                        PDXObject xObject = resources.getXObject(xObjectName);
                        if (xObject instanceof PDImageXObject image) {
                            BufferedImage bufferedImage = image.getImage();
                            // 将图像保存到临时文件
                            String tempImageFile = ocrService.generateOrcTempImagePath(username);
                            ImageIO.write(bufferedImage, "png", new File(tempImageFile));
                            try {
                                // 使用 Tesseract 进行 OCR 识别
                                String ocrResult = ocrService.doOCR(tempImageFile, ocrService.generateOrcTempImagePath(username));
                                content.append(ocrResult);
                            } finally {
                                // 删除临时文件
                                FileUtil.del(tempImageFile);
                            }
                        }
                    }
                }
            }
            return content.toString();
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        } finally {
            taskProgressService.removeTaskProgress(file);
        }
        return null;
    }

    public String readEpubContent(File file, String fileId) {
        try (InputStream fileInputStream = new FileInputStream(file)) {
            // 打开 EPUB 文件
            EpubReader epubReader = new EpubReader();
            Book book = epubReader.readEpub(fileInputStream);

            // 生成封面图像
            String username = commonFileService.getUsernameByAbsolutePath(Path.of(file.getAbsolutePath()));
            if (StrUtil.isNotBlank(fileId)) {
                File coverFile = FileContentUtil.epubCoverImage(book, videoProcessService.getVideoCacheDir(username, fileId));
                commonFileService.updateCoverFileDocument(fileId, coverFile);
            }

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
            return content.toString();
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
        return null;
    }

    public String readPPTContent(File file) {
        StringBuilder stringBuilder = new StringBuilder();
        String fileName = file.getName().toLowerCase();

        try (FileInputStream fis = new FileInputStream(file)) {
            if (fileName.endsWith(".pptx")) {
                // 读取 .pptx 文件
                try (XMLSlideShow pptx = new XMLSlideShow(fis)) {
                    for (XSLFSlide slide : pptx.getSlides()) {
                        for (XSLFShape shape : slide.getShapes()) {
                            if (shape instanceof XSLFTextShape textShape) {
                                stringBuilder.append(textShape.getText()).append(" ");
                            }
                        }
                    }
                }
            } else if (fileName.endsWith(".ppt")) {
                // 读取 .ppt 文件
                try (HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
                    for (org.apache.poi.hslf.usermodel.HSLFSlide slide : ppt.getSlides()) {
                        for (HSLFShape shape : slide.getShapes()) {
                            if (shape instanceof HSLFTextShape textShape) {
                                stringBuilder.append(textShape.getText()).append(" ");
                            }
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("不支持的文件格式");
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }

        return stringBuilder.toString().trim();
    }

    public String readWordContent(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            try {
                // 尝试读取 OOXML 格式 (.docx) 文件
                XWPFDocument document = new XWPFDocument(fis);
                StringBuilder stringBuilder = new StringBuilder();
                List<XWPFParagraph> paragraphs = document.getParagraphs();
                for (XWPFParagraph para : paragraphs) {
                    stringBuilder.append(para.getText());
                }
                return stringBuilder.toString();
            } catch (OLE2NotOfficeXmlFileException e) {
                // 如果文件不是 OOXML 格式，尝试读取 OLE2 格式 (.doc) 文件
                try (FileInputStream fis2 = new FileInputStream(file);
                     POIFSFileSystem poifs = new POIFSFileSystem(fis2);
                     HWPFDocument doc = new HWPFDocument(poifs)) {
                    WordExtractor extractor = new WordExtractor(doc);
                    return extractor.getText();
                }
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
        return null;
    }

    private static final Pattern NON_NUMERIC_PATTERN = Pattern.compile("[^0-9]+");

    public String readExcelContent(File file) {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file)) {
            Workbook workbook = null;

            if (file.getName().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (file.getName().endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IllegalArgumentException("不支持的文件格式");
            }

            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell);
                        // 过滤掉数字，只保留文字
                        if (NON_NUMERIC_PATTERN.matcher(cellValue).matches()) {
                            content.append(cellValue).append(" ");
                        }
                    }
                }
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
        return content.toString().trim();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return Double.toString(cell.getNumericCellValue());
                }
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
            default:
                return "";
        }
    }
}
