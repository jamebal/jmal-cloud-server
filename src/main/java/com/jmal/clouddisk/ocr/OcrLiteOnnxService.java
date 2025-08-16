package com.jmal.clouddisk.ocr;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;

@Service("ocrLiteOnnx")
@RequiredArgsConstructor
@Slf4j
public class OcrLiteOnnxService implements IOcrService {

    public final FileProperties fileProperties;

    /**
     * 【已优化】对指定的图片执行 OCR, 并将结果直接流式传输到 Writer.
     *
     * @param writer        用于接收 OCR 结果的 Writer.
     * @param imagePath     需要识别的图片文件的路径.
     * @param tempImagePath 临时图片文件的路径 (用于 OCR 后删除).
     */
    @Override
    public void doOCR(Writer writer, String imagePath, String tempImagePath) {
        if (CharSequenceUtil.isBlank(imagePath)) {
            return;
        }
        try {
            ProcessBuilder processBuilder = getOcrLiteOnnxProcessBuilder(imagePath);
            // 调用流式处理方法
            CommonOcrService.executeAndPipeOutput(writer, processBuilder);
        } catch (IOException | InterruptedException e) {
            log.warn("Error while performing OCR with OcrLiteOnnx: {}", e.getMessage(), e);
            // 如果在等待时被中断，恢复中断状态
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            // 无论成功与否，都删除用于 OCR 的临时图片
            FileUtil.del(tempImagePath);
        }
    }

    /**
     * 构建 OcrLiteOnnx 命令的 ProcessBuilder.
     * @param inputPath 输入图片路径
     * @return ProcessBuilder
     */
    private ProcessBuilder getOcrLiteOnnxProcessBuilder(String inputPath) {
        return new ProcessBuilder(
                Constants.OCR_LITE_ONNX,
                "--models", fileProperties.getOcrLiteONNXModelPath(),
                "--det", "dbnet.onnx",
                "--rec", "crnn_lite_lstm.onnx",
                "--keys", "keys.txt",
                "--image", inputPath,
                "--numThread", "1",
                "--padding", "40",
                "--maxSideLen", "2048",
                "--boxScoreThresh", "0.6",
                "--boxThresh", "0.3",
                "--unClipRatio", "2.0",
                "--doAngle", "0",
                "--mostAngle", "0"
        );
    }
}
