package com.jmal.clouddisk.ocr;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.media.FFMPEGCommand;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.jmal.clouddisk.util.FFMPEGUtils.getWaitingForResults;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommonOcrService {

    /**
     * 获取OCR识别之前预处理的图片, 使其更易识别
     * 使用ffmpeg调整灰度和对比度
     * @param inputPath 原始图片路径
     * @return 预处理后的图片路径
     */
    public static String getPreprocessedOCRImage(String inputPath, String outputPath) {
        if (FFMPEGCommand.hasNoFfmpeg()) {
            return outputPath;
        }
        if (FileUtil.exist(outputPath)) {
            return outputPath;
        }
        try {
            ProcessBuilder processBuilder = getPreOCRImageProcessBuilder(inputPath, outputPath);
            Process process = processBuilder.start();
            return getWaitingForResults(outputPath, processBuilder, process);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            return null;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static ProcessBuilder getPreOCRImageProcessBuilder(String inputPath, String outputPath) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                Constants.FFMPEG,
                "-i", inputPath,
                "-vf", "format=gray,eq=contrast=1.5:brightness=0.1",
                outputPath
        );
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    /**
     * 执行外部命令并将其标准输出直接通过管道传输到指定的 Writer.
     * 这种方法内存效率高，因为它使用缓冲区进行流式传输，而不是将整个输出加载到内存。
     *
     * @param writer         输出目标.
     * @param processBuilder 配置好的 ProcessBuilder 实例.
     */
    public static void executeAndPipeOutput(Writer writer, ProcessBuilder processBuilder) throws IOException, InterruptedException {
        log.debug("Executing and piping output for command: {}", String.join(" ", processBuilder.command()));
        Process process = processBuilder.start();

        // 使用 try-with-resources 确保流被正确关闭
        // 将进程的字节输出流转换为使用 UTF-8 编码的字符流
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192]; // 8KB 缓冲区
            int charsRead;
            // 循环读取，直到流末尾
            while ((charsRead = reader.read(buffer)) != -1) {
                // 将读取到的字符直接写入目标 writer
                writer.write(buffer, 0, charsRead);
            }
        }

        // 检查进程是否成功完成，设置一个合理的超时
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("OcrLiteOnnx command timed out");
        }

        if (process.exitValue() != 0) {
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            log.error("Command failed with exit code {}: {}", process.exitValue(), String.join(" ", processBuilder.command()));
            log.error("Stderr: {}", error);
        }
    }

}
