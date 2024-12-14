package com.jmal.clouddisk.ocr;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.media.FFMPEGCommand;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import static com.jmal.clouddisk.util.FFMPEGUtils.getWaitingForResults;


@Service
@RequiredArgsConstructor
@Slf4j
public class OcrLiteOnnxService implements IOcrService {

    private final CommonOcrService commonOcrService;

    public final FileProperties fileProperties;

    // 初始设置为1个并发请求
    private final Semaphore semaphore = new Semaphore(1);

    public String doOCR(String imagePath, String tempImagePath) {
        if (StrUtil.isBlank(imagePath)) {
            return "";
        }
        String resultTxtPath = null;
        try {
            // 获取许可，如果没有可用许可则会阻塞
            semaphore.acquire();
            resultTxtPath = getResultText(imagePath, imagePath + "-result.txt");
            if (!FileUtil.isFile(resultTxtPath)) {
                return "";
            }
            return FileUtil.readUtf8String(resultTxtPath);
        } catch (Exception e) {
            log.warn("Error while performing OCR: {}", e.getMessage(), e);
        } finally {
            FileUtil.del(tempImagePath);
            if (FileUtil.isFile(resultTxtPath)) {
                FileUtil.del(resultTxtPath);
            }
            // 释放许可
            semaphore.release();
        }
        return "";
    }

    /**
     * 动态调整并发数量
     * @param maxConcurrentRequests 最大并发请求数
     */
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        int currentPermits = semaphore.availablePermits();
        if (maxConcurrentRequests > currentPermits) {
            semaphore.release(maxConcurrentRequests - currentPermits);
        } else if (maxConcurrentRequests < currentPermits) {
            semaphore.drainPermits(); // 清空所有许可
            semaphore.release(maxConcurrentRequests);
        }
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
            return getWaitingForResults(outputPath, processBuilder, process, 60);
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
                "--numThread", "1",
                "--padding", "40",
                "--maxSideLen", "1024",
                "--boxScoreThresh", "0.6",
                "--boxThresh", "0.3",
                "--unClipRatio", "2.0",
                "--doAngle", "0",
                "--mostAngle", "0",
                "--outputResultImg", "0"
        );
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }
}
