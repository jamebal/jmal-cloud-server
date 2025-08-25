package com.jmal.clouddisk.lucene;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.ocr.OcrService;
import com.jmal.clouddisk.service.impl.PathService;
import com.jmal.clouddisk.util.FileContentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个使用 poppler-utils 命令行工具来处理 PDF 文件的服务.
 * 这个类完全不依赖 org.apache.pdfbox, 从而避免了 GraalVM Native Image 的 AWT 问题.
 * 依赖的外部命令行工具:
 * - pdfinfo: 获取 PDF 元数据 (如页数).
 * - pdftotext: 从页面提取文本.
 * - pdfimages: 检查页面是否包含图片.
 * - pdftoppm: 将页面渲染为图片 (用于封面和 OCR).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PopplerPdfReader {

    private final CoverFileService coverFileService;
    private final OcrService ocrService;
    private final ImageMagickProcessor imageMagickProcessor;
    private final PathService pathService;

    // 用于从 'pdfinfo' 的输出中解析页数的正则表达式
    private static final Pattern PAGES_PATTERN = Pattern.compile("Pages:\\s*(\\d+)");

    /**
     * 主入口方法，替代原有的 readPdfContent.
     * @param file PDF 文件
     * @param fileId 文件 ID
     * @param writer 用于写入提取内容的 Writer
     */
    public void readPdfContent(File file, String fileId, Writer writer) {
        try {
            String username = pathService.getUsernameByAbsolutePath(Path.of(file.getAbsolutePath()));
            int totalPages = getPdfTotalPages(file);
            log.debug("PDF '{}' has {} pages.", file.getName(), totalPages);

            // 1. 生成封面图像 (替代 FileContentUtil.pdfCoverImage)
            if (CharSequenceUtil.isNotBlank(fileId)) {
                File coverFile = FileContentUtil.pdfCoverImage(file, pathService.getVideoCacheDir(username, fileId));
                coverFileService.updateCoverFileDocument(fileId, coverFile);
            }

            // 2. 逐页处理
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                // poppler 工具使用 1-based 索引
                int pageNumber = pageIndex + 1;
                log.debug("Processing page {} of {}", pageNumber, totalPages);
                readPdfOfPage(file, pageNumber, totalPages, writer, username);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PDF processing was interrupted for file: {}", file.getAbsolutePath(), e);
        } catch (IOException e) {
            log.error("Failed to process PDF file: {}", file.getAbsolutePath(), e);
            FileContentUtil.readFailed(file, e);
        }
    }

    private void readPdfOfPage(File file, int pageNumber, int totalPages, Writer writer, String username) throws IOException, InterruptedException {
        // 1. 提取文本
        String text = extractTextFromPage(file, pageNumber).trim();

        // 2. 检查页面是否包含图片 (替代 checkPageContent)
        boolean hasImages = pageContainsImages(file, pageNumber);

        // 3. 如果页面包含文字，写入 writer
        if (!text.isEmpty()) {
            writer.write(text);
        }

        // 4. 如果 (页面包含图片 或 页面没有文字) 并且 OCR 已启用，则进行 OCR
        boolean shouldOcr = hasImages || text.isEmpty();
        boolean ocrEnabled = BooleanUtil.isTrue(ocrService.getOcrConfig().getEnable());

        if (shouldOcr && ocrEnabled) {
            log.debug("Page {} requires OCR (has images: {}, text is empty: {}).", pageNumber, hasImages, text.isEmpty());

            String tempImage = imageMagickProcessor.generateOrcTempImagePath(username);
            // 用 pdftoppm 将该页转为图片 300 DPI for OCR
            String imageForOcr = renderPageToImage(file, pageNumber, 300, tempImage);
            ocrService.extractPageWithOCR(writer, file, imageForOcr, pageNumber, totalPages, username);
            log.debug("Page {} has OCR test: {}", pageNumber, writer.toString());
        }
    }

    // --- Poppler-Utils 封装 ---

    private int getPdfTotalPages(File pdfFile) throws IOException, InterruptedException {
        String output = executeCommand("pdfinfo", pdfFile.getAbsolutePath());
        Matcher matcher = PAGES_PATTERN.matcher(output);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new IOException("Could not determine number of pages from pdfinfo output.");
    }

    private String extractTextFromPage(File pdfFile, int pageNumber) throws IOException, InterruptedException {
        // -f: first page, -l: last page, -: output to stdout
        return executeCommand("pdftotext", "-f", String.valueOf(pageNumber), "-l", String.valueOf(pageNumber), pdfFile.getAbsolutePath(), "-");
    }

    /**
     * 替代 checkPageContent. 使用 'pdfimages -list' 命令.
     * 如果命令输出的行数 > 2 (标题行 + "total" 行), 则说明有图片.
     */
    private boolean pageContainsImages(File pdfFile, int pageNumber) throws IOException, InterruptedException {
        String output = executeCommand("pdfimages", "-f", String.valueOf(pageNumber), "-l", String.valueOf(pageNumber), "-list", pdfFile.getAbsolutePath());
        // The output of `pdfimages -list` has a 2-line header. More than 2 lines means images were found.
        return output.lines().count() > 2;
    }

    public String renderPageToImage(File pdfFile, int pageNumber, int dpi, String tempImage) throws IOException, InterruptedException {
        // 使用 pdftoppm 将特定页面渲染为高分辨率图片
        executeCommand("pdftoppm",
                "-png",
                "-f", String.valueOf(pageNumber),
                "-l", String.valueOf(pageNumber),
                "-r", String.valueOf(dpi),
                "-singlefile",
                pdfFile.getAbsolutePath(),
                tempImage.replace(".png", "")
        );
        return tempImage;
    }

    public static String executeCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        String output;
        try (InputStream processStream = process.getInputStream()) {
            output = new String(processStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + String.join(" ", command));
        }

        if (process.exitValue() != 0) {
            // 对于某些命令 (如 pdfimages), 即使没有图片 (非错误), 也可能有非零退出码。
            log.warn("Command finished with non-zero exit code {}: {}", process.exitValue(), String.join(" ", command));
        }
        return output;
    }
}
