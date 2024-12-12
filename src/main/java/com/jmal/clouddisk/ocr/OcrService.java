package com.jmal.clouddisk.ocr;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.ObjectId;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.media.FFMPEGCommand;
import com.jmal.clouddisk.service.Constants;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.jmal.clouddisk.util.FFMPEGUtils.getWaitingForResults;


@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final ThreadLocal<Tesseract> tesseractThreadLocal;

    private final ThreadLocal<InferenceEngine> papidOcrThreadLocal;

    private final FileProperties fileProperties;

    public String doOCR(String imagePath, String tempImagePath) {
        try {
            if (StrUtil.isBlank(imagePath)) {
                return "";
            }
            if (StrUtil.isBlank(tempImagePath)) {
                tempImagePath = generateOrcTempImagePath(null);
            }
            // 预处理后的图片
            String preprocessedOCRImage = getPreprocessedOCRImage(imagePath, tempImagePath);
            if (StrUtil.isBlank(preprocessedOCRImage)) {
                return "";
            }
            TimeInterval interval = new TimeInterval();
            File imageFile = new File(preprocessedOCRImage);
            // ITesseract tesseract = tesseractThreadLocal.get();
            // String content = tesseract.doOCR(imageFile);
            InferenceEngine engine = papidOcrThreadLocal.get();
            String content = engine.runOcr(preprocessedOCRImage).getStrRes();
            log.info("OCR result: {}", content);
            log.info("OCR time consuming: {}", interval.intervalMs());
            return content;
        } catch (Exception e) {
            log.warn("Error while performing OCR: {}", e.getMessage(), e);
        } finally {
            FileUtil.del(tempImagePath);
        }
        return "";
    }

    /**
     * 生成一个临时的图片路径
     */
    public String generateOrcTempImagePath(String username) {
        Path tempPath;
        if (StrUtil.isBlank(username)) {
            tempPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir());
        } else {
            tempPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username);
        }
        if (!FileUtil.exist(tempPath.toString())) {
            FileUtil.mkdir(tempPath.toString());
        }
        return Paths.get(tempPath.toString(), ObjectId.next(true) + "_temp_ocr.png").toString();
    }

    /**
     * 获取OCR识别之前预处理的图片, 使其更易识别
     * 使用ffmpeg调整灰度和对比度
     * @param inputPath 原始图片路径
     * @return 预处理后的图片路径
     */
    public String getPreprocessedOCRImage(String inputPath, String outputPath) {
        if (FFMPEGCommand.hasNoFFmpeg()) {
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
}
