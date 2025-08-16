package com.jmal.clouddisk.ocr;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;


@Service("tesseract")
@RequiredArgsConstructor
@Slf4j
@Primary
public class TesseractService implements IOcrService {

    private final TesseractOcrConfig tesseractOcrConfig;

    private final ImageMagickProcessor imageMagickProcessor;

    @Override
    public void doOCR(Writer writer, String imagePath, String tempImagePath) {
        if (CharSequenceUtil.isBlank(imagePath)) {
            return;
        }
        if (CharSequenceUtil.isBlank(tempImagePath)) {
            tempImagePath = imageMagickProcessor.generateOrcTempImagePath(null);
        }
        doOCR(writer, imagePath, tempImagePath, tesseractOcrConfig.getDataPath());
    }

    public void doOCR(Writer writer, String imagePath, String tempImagePath, String tessdataDir) {
        try {

            // 预处理后的图片
            String preprocessedOCRImage = CommonOcrService.getPreprocessedOCRImage(imagePath, tempImagePath);
            if (CharSequenceUtil.isBlank(preprocessedOCRImage)) {
                return;
            }

            // 构建 Tesseract 命令
            ProcessBuilder processBuilder = getOcrLiteOnnxProcessBuilder(preprocessedOCRImage, tessdataDir);

            // 调用流式处理方法
            CommonOcrService.executeAndPipeOutput(writer, processBuilder);
        } catch (IOException | InterruptedException e) {
            log.error("Tesseract ORC operation failed for file '{}'", imagePath, e);
            Thread.currentThread().interrupt();
        } finally {
            FileUtil.del(tempImagePath);
        }
    }

    /**
     * 构建 Tesseract 命令的 ProcessBuilder.
     * @param inputPath 输入图片路径
     * @param tessdataDir 自定义的 tessdata 目录
     * @return ProcessBuilder
     */
    private ProcessBuilder getOcrLiteOnnxProcessBuilder(String inputPath, String tessdataDir) {
        // 将结果输出到标准输出
        // 使用简体中文语言包
        // 使用配置的 tessdata 目录
        return new ProcessBuilder(
                "tesseract",
                inputPath,
                "stdout", // 将结果输出到标准输出
                "-l", "chi_sim", // 使用简体中文语言包
                "--tessdata-dir",
                tessdataDir != null ? tessdataDir : tesseractOcrConfig.getDataPath() // 使用配置的 tessdata 目录
        );
    }

}
