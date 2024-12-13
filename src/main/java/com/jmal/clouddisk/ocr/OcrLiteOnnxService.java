package com.jmal.clouddisk.ocr;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.media.FFMPEGCommand;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.jmal.clouddisk.util.FFMPEGUtils.getWaitingForResults;


@Service
@RequiredArgsConstructor
@Slf4j
public class OcrLiteOnnxService implements IOcrService {

    private final CommonOcrService commonOcrService;

    public final FileProperties fileProperties;

    public String doOCR(String imagePath, String tempImagePath) {
        try {
            if (StrUtil.isBlank(imagePath)) {
                return "";
            }
            TimeInterval interval = new TimeInterval();
            String resultTxtPath = getResultText(imagePath, imagePath + "-result.txt");
            String content = FileUtil.readUtf8String(resultTxtPath);
            log.info("OcrLiteOnnx OCR result: {}", content);
            log.info("OcrLiteOnnx OCR time consuming: {}", interval.intervalMs());
            return content;
        } catch (Exception e) {
            log.warn("Error while performing OCR: {}", e.getMessage(), e);
        } finally {
            FileUtil.del(tempImagePath);
        }
        return "";
    }

    public String generateOrcTempImagePath(String username) {
        return commonOcrService.generateOrcTempImagePath(username);
    }

    /**
     * 获取OCR识别结果
     * @param inputPath 输入图片路径
     * @param outputPath 输出文件路径
     * @return String
     */
    public String getResultText(String inputPath, String outputPath) {
        if (FFMPEGCommand.hasNoFFmpeg()) {
            return outputPath;
        }
        if (FileUtil.exist(outputPath)) {
            return outputPath;
        }
        try {
            ProcessBuilder processBuilder = getOcrLiteOnnxProcessBuilder(inputPath);
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

    /**
     * 使用ocr_lite_onnx进行OCR识别
     * @param inputPath 输入图片路径
     * @return ProcessBuilder
     */
    private ProcessBuilder getOcrLiteOnnxProcessBuilder(String inputPath) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                Constants.OCR_LITE_ONNX,
                "--models", fileProperties.getOcrLiteONNXModelPath(),
                "--det", "dbnet.onnx",
                "--rec", "crnn_lite_lstm.onnx",
                "--keys", "keys.txt",
                "--image", inputPath,
                "--numThread", "4",
                "--padding", "0",
                "--maxSideLen", "1024",
                "--boxScoreThresh", "0.6",
                "--boxThresh", "0.3",
                "--unClipRatio", "2.0",
                "--doAngle", "1",
                "--mostAngle", "1",
                "--outputResultImg", "0"
        );
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }
}
