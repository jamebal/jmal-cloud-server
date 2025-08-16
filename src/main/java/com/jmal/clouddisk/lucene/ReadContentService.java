package com.jmal.clouddisk.lucene;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.PathService;
import com.jmal.clouddisk.util.FileContentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.epub.EpubReader;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadContentService {

    private final PathService pathService;

    private final CoverFileService coverFileService;


    /**
     * 将 DWG 文件转换为 MXWeb 文件
     *
     * @param file   文件
     * @param fileId 文件 ID
     */
    public void dwg2mxweb(File file, String fileId) {
        String username = pathService.getUsernameByAbsolutePath(Path.of(file.getAbsolutePath()));
        // 生成封面图像
        if (CharSequenceUtil.isNotBlank(fileId)) {
            String outputName = file.getName() + Constants.MXWEB_SUFFIX;
            FileContentUtil.dwgConvert(file.getAbsolutePath(), pathService.getVideoCacheDir(username, fileId), outputName);
        }
    }

    public void readEpubContent(File file, String fileId, Writer writer) {
        try (InputStream fileInputStream = new FileInputStream(file)) {
            // 打开 EPUB 文件
            EpubReader epubReader = new EpubReader();
            Book book = epubReader.readEpub(fileInputStream);

            // 生成封面图像
            String username = pathService.getUsernameByAbsolutePath(Path.of(file.getAbsolutePath()));
            if (CharSequenceUtil.isNotBlank(fileId)) {
                File coverFile = FileContentUtil.epubCoverImage(book, pathService.getVideoCacheDir(username, fileId));
                coverFileService.updateCoverFileDocument(fileId, coverFile);
            }

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
                writer.write(textContent);
                is.close();
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
    }

    public void readPPTContent(File file, Writer writer) {
        String fileName = file.getName().toLowerCase();

        try (FileInputStream fis = new FileInputStream(file)) {
            if (fileName.endsWith(".pptx")) {
                // 读取 .pptx 文件
                try (XMLSlideShow pptx = new XMLSlideShow(fis)) {
                    readSlides(pptx.getSlides(), writer);
                }
            } else if (fileName.endsWith(".ppt")) {
                // 读取 .ppt 文件
                try (HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
                    readSlides(ppt.getSlides(), writer);
                }
            } else {
                throw new IllegalArgumentException("Unsupported file format: " + fileName);
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
    }

    // 通用方法读取幻灯片中的文本内容
    private void readSlides(Iterable<?> slides, Writer writer) throws IOException {
        for (Object slide : slides) {
            if (slide instanceof XSLFSlide xslfSlide) {
                for (XSLFShape shape : xslfSlide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        writer.write(textShape.getText());
                        writer.write(" ");
                    }
                }
            } else if (slide instanceof HSLFSlide hslfSlide) {
                for (HSLFShape shape : hslfSlide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        writer.write(textShape.getText());
                        writer.write(" ");
                    }
                }
            }
        }
    }

    public void readWordContent(File file, Writer writer) {
        try (FileInputStream fis = new FileInputStream(file)) {
            try {
                // 读取 DOCX 文件
                XWPFDocument document = new XWPFDocument(fis);

                // 1. 读取段落
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    writer.write(paragraph.getText());
                    writer.write("\n");
                }

                // 2. 读取表格
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            writer.write(cell.getText());
                            writer.write("\t");
                        }
                        writer.write("\n");
                    }
                }

                // 3. 读取页眉
                for (XWPFHeader header : document.getHeaderList()) {
                    writer.write(header.getText());
                    writer.write("\n");
                }

                // 4. 读取页脚
                for (XWPFFooter footer : document.getFooterList()) {
                    writer.write(footer.getText());
                    writer.write("\n");
                }
            } catch (OLE2NotOfficeXmlFileException e) {
                // 读取 DOC 文件
                try (FileInputStream fis2 = new FileInputStream(file);
                     POIFSFileSystem poifs = new POIFSFileSystem(fis2);
                     HWPFDocument doc = new HWPFDocument(poifs)) {
                    WordExtractor extractor = new WordExtractor(doc);
                    writer.write(extractor.getText());
                }
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
    }

    // 匹配包含至少一个中文或英文字符的字符串
    private static final Pattern TEXT_PATTERN = Pattern.compile(".*[a-zA-Z一-龥]+.*");

    public void readExcelContent(File file, Writer writer) {
        try (FileInputStream fis = new FileInputStream(file)) {
            try (Workbook workbook = createWorkbook(file, fis)) {
                for (Sheet sheet : workbook) {
                    readSheetContent(sheet, writer);
                }
            }
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        } catch (IllegalArgumentException e) {
            log.warn("Unsupported file format: {}", file.getName());
        }
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

    private void readSheetContent(Sheet sheet, Writer writer) throws IOException {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell);
                // 包含至少一个中文或英文字符
                if (TEXT_PATTERN.matcher(cellValue).matches()) {
                    writer.write(cellValue);
                    writer.write(" ");
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
