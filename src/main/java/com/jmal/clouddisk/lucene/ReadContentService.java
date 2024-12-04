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
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
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
import org.apache.poi.xwpf.usermodel.*;
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

    public static boolean checkPageContent(PDDocument document, int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex); // 获取页面
        // 检查图片内容
        PDResources resources = page.getResources();
        for (COSName xObjectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(xObjectName);
            if (xObject instanceof PDImageXObject) {
                // 如果找到至少一张图片，则可以提前退出
                return true;
            }
        }
        return false;
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
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            PDFTextStripper pdfStripper = new PDFTextStripper();

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) { // 使用 0-based 索引
                pdfStripper.setStartPage(pageIndex + 1); // PDFTextStripper 使用 1-based 索引
                pdfStripper.setEndPage(pageIndex + 1);
                String text = pdfStripper.getText(document).trim();

                // 如果页面包含文字，添加提取的文字
                if (!text.isEmpty()) {
                    content.append(text);
                }
                // 如果页面包含图片或没有文字，则进行 OCR
                if (checkPageContent(document, pageIndex) || text.isEmpty()) {
                    content.append(extractPageWithOCR(pdfRenderer, pageIndex, username));
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

    private String extractPageWithOCR(PDFRenderer pdfRenderer, int pageIndex, String username) {
        try {
            BufferedImage pageImage = pdfRenderer.renderImageWithDPI(pageIndex, 300);
            String tempImageFile = ocrService.generateOrcTempImagePath(username);
            ImageIO.write(pageImage, "png", new File(tempImageFile));
            try {
                // 使用 OCR 识别页面内容
                return ocrService.doOCR(tempImageFile, ocrService.generateOrcTempImagePath(username));
            } finally {
                FileUtil.del(tempImageFile);
            }
        } catch (Exception e) {
            log.error("Error processing page {}", pageIndex + 1, e);
            return "";
        }
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
                    readSlides(pptx.getSlides(), stringBuilder);
                }
            } else if (fileName.endsWith(".ppt")) {
                // 读取 .ppt 文件
                try (HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
                    readSlides(ppt.getSlides(), stringBuilder);
                }
            } else {
                throw new IllegalArgumentException("Unsupported file format: " + fileName);
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
        return stringBuilder.toString().trim();
    }

    // 通用方法读取幻灯片中的文本内容
    private void readSlides(Iterable<?> slides, StringBuilder content) {
        for (Object slide : slides) {
            if (slide instanceof XSLFSlide xslfSlide) {
                for (XSLFShape shape : xslfSlide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        content.append(textShape.getText()).append(" ");
                    }
                }
            } else if (slide instanceof HSLFSlide hslfSlide) {
                for (HSLFShape shape : hslfSlide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        content.append(textShape.getText()).append(" ");
                    }
                }
            }
        }
    }
    public String readWordContent(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            try {
                // 读取 DOCX 文件
                XWPFDocument document = new XWPFDocument(fis);
                StringBuilder stringBuilder = new StringBuilder();

                // 1. 读取段落
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    stringBuilder.append(paragraph.getText()).append("\n");
                }

                // 2. 读取表格
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            stringBuilder.append(cell.getText()).append("\t");
                        }
                        stringBuilder.append("\n");
                    }
                }

                // 3. 读取页眉
                for (XWPFHeader header : document.getHeaderList()) {
                    stringBuilder.append(header.getText()).append("\n");
                }

                // 4. 读取页脚
                for (XWPFFooter footer : document.getFooterList()) {
                    stringBuilder.append(footer.getText()).append("\n");
                }
                return stringBuilder.toString();
            } catch (OLE2NotOfficeXmlFileException e) {
                // 读取 DOC 文件
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

    // 匹配包含至少一个中文或英文字符的字符串
    private static final Pattern TEXT_PATTERN = Pattern.compile(".*[a-zA-Z一-龥]+.*");

    public String readExcelContent(File file) {
        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file)) {
            try (Workbook workbook = createWorkbook(file, fis)) {
                for (Sheet sheet : workbook) {
                    readSheetContent(sheet, content);
                }
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        } catch (IllegalArgumentException e) {
            log.warn("Unsupported file format: {}", file.getName());
        }
        return content.toString().trim();
    }

    private Workbook createWorkbook(File file, FileInputStream fis) throws IOException {
        if (file.getName().endsWith(".xlsx")) {
            return new XSSFWorkbook(fis);
        } else if (file.getName().endsWith(".xls")) {
            return new HSSFWorkbook(fis);
        } else {
            throw new IllegalArgumentException("Unsupported file format");
        }
    }

    private void readSheetContent(Sheet sheet, StringBuilder content) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell);
                // 包含至少一个中文或英文字符
                if (TEXT_PATTERN.matcher(cellValue).matches()) {
                    content.append(cellValue).append(" ");
                }
            }
        }
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
