package com.jmal.clouddisk.util;

import com.jmal.clouddisk.lucene.PopplerPdfReader;
import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.jmal.clouddisk.util.FFMPEGUtils.getWaitingForResults;

@Slf4j
public class FileContentUtil {

    private static final String mxcadassemblyPath = "/usr/local/mxcad";

    public static void readFailed(File file, IOException e) {
        log.warn("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage());
    }

    public static File getCoverPath(String outputPath) {
        return new File(outputPath, "cover.jpg");
    }

    public static File pdfCoverImage(File pdfFile, String outputDir) {
        try {
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);
            Path coverPath = outputPath.resolve("cover.png");
            PopplerPdfReader.executeCommand("pdftoppm", "-png", "-f", "1", "-l", "1", "-singlefile", pdfFile.getAbsolutePath(), coverPath.toString().replace(".png", ""));
            return coverPath.toFile();
        } catch (Throwable e) {
            log.warn("PDF 文件封面图像生成失败: {}, file: {}", e.getMessage(), pdfFile.getName());
            return null;
        }
    }

    public static File epubCoverImage(File file, Book book, String outputPath) {
        // 获取封面图片
        Resource coverImage = book.getCoverImage();
        if (coverImage != null) {
            File coverImageFile = getCoverPath(outputPath);
            try (InputStream coverImageInputStream = coverImage.getInputStream();
                 OutputStream coverImageOutput = new FileOutputStream(coverImageFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = coverImageInputStream.read(buffer)) != -1) {
                    coverImageOutput.write(buffer, 0, bytesRead);
                }
                return coverImageFile;
            } catch (Throwable e) {
                log.warn("epub 文件封面图像生成失败: {}, 文件: {}" ,e.getMessage(), file.getAbsoluteFile());
                return null;
            }
        }
        log.warn("epub 文件封面图像生成失败: 未找到封面图片, 文件: {}", file.getAbsoluteFile());
        return null;
    }

    /**
     * 将dwg文件转换为mxweb文件的命令
     *
     * @param sourceFile dwg文件绝对路径
     * @param outputPath 输出路径
     * @param outputFile 输出文件名
     */
    public static void dwgConvert(String sourceFile, String outputPath, String outputFile) {
        if (!Paths.get(mxcadassemblyPath).toFile().exists()) {
            return;
        }
        try {
            ProcessBuilder processBuilder = dwgConvertCommand(sourceFile, outputPath, outputFile);
            Process process = processBuilder.start();
            outputPath = getWaitingForResults(outputPath, processBuilder, process, 60);
            if (outputPath != null) {
                log.info("dwg文件转换成功, outputPath: {}", outputPath);
            }
        } catch (InterruptedException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 将dwg文件转换为mxweb文件的命令
     * @param sourceFile dwg文件绝对路径
     * @param outPath 输出路径
     * @param outFile 输出文件名
     * @return ProcessBuilder
     */
    private static ProcessBuilder dwgConvertCommand(String sourceFile, String outPath, String outFile) {
        // 转换文件路径.
        String command = "./mxcadassembly";
        // 转换参数。
        String path = "{'srcpath':'" + sourceFile + "','outpath':'" + outPath + "','outname':'" + outFile + "'}";
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.redirectErrorStream(true);
        List<String> commands = new ArrayList<>();
        commands.add(command);
        commands.add(path);
        processBuilder.command(commands).directory(new File(mxcadassemblyPath));
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }
}
