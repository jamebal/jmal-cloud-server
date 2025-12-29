package com.jmal.clouddisk.lucene;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.service.impl.PathService;
import com.jmal.clouddisk.util.FileContentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.epub.EpubReader;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadContentService {

    private final PathService pathService;

    private final CoverFileService coverFileService;

    private final DataFormatter dataFormatter = new DataFormatter();

    // 匹配包含至少一个中文或英文字符的字符串
    private static final Pattern TEXT_PATTERN = Pattern.compile(".*[a-zA-Z一-龥]+.*");

    public void readEpubContent(File file, String fileId, Writer writer) {
        try (InputStream fileInputStream = new FileInputStream(file)) {
            // 打开 EPUB 文件
            EpubReader epubReader = new EpubReader();
            Book book = epubReader.readEpub(fileInputStream);

            // 生成封面图像
            String username = pathService.getUsernameByAbsolutePath(Path.of(file.getAbsolutePath()));
            if (CharSequenceUtil.isNotBlank(fileId)) {
                File coverFile = FileContentUtil.epubCoverImage(file, book, pathService.getVideoCacheDir(username, fileId));
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
        try (FileInputStream fis = new FileInputStream(file)) {
            try (SlideShow<?, ?> slideShow = SlideShowFactory.create(fis)) {
                try (SlideShowExtractor<?, ?> extractor = new SlideShowExtractor<>(slideShow)) {
                    // 设置提取备注页文本
                    extractor.setNotesByDefault(true);
                    String text = extractor.getText();
                    if (text != null) {
                        writer.write(text);
                    }
                }
            }
        } catch (Exception e) {
            FileContentUtil.readFailed(file, e);
        }
    }

    public void readWordContent(File file, Writer writer) {
        try (FileInputStream fis = new FileInputStream(file)) {
            String fileName = file.getName().toLowerCase();

            if (fileName.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(fis);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    writer.write(extractor.getText());
                }
            } else if (fileName.endsWith(".doc")) {
                try (HWPFDocument doc = new HWPFDocument(fis);
                     WordExtractor extractor = new WordExtractor(doc)) {
                    writer.write(extractor.getText());
                }
            }
        } catch (Exception e) {
            FileContentUtil.readFailed(file, e);
        }
    }

    public void readExcelContent(File file, Writer writer) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = createWorkbook(file, fis)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String cellValue = dataFormatter.formatCellValue(cell);
                        if (cellValue != null && TEXT_PATTERN.matcher(cellValue).matches()) {
                            writer.write(cellValue);
                            writer.write(" ");
                        }
                    }
                    // 换行增加索引分词准确度
                    writer.write("\n");
                }
            }
        } catch (Exception e) {
            FileContentUtil.readFailed(file, e);
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

}
