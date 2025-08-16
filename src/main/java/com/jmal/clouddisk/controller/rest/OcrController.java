package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.ocr.OcrService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringWriter;

@RestController
@RequiredArgsConstructor
@Tag(name = "OCR")
@Slf4j
public class OcrController {

    private final OcrService ocrService;
    private final ImageMagickProcessor imageMagickProcessor;
    private final UserLoginHolder userLoginHolder;

    @GetMapping("/ocr")
    public String performOcr(@RequestParam String fileUrl) {
        String tempImagePath = imageMagickProcessor.generateOrcTempImagePath(userLoginHolder.getUsername());
        try (StringWriter contentWriter = new StringWriter()) {
            HttpUtil.downloadFile(fileUrl, tempImagePath);
            TimeInterval timeInterval = new TimeInterval();
            timeInterval.start();
            ocrService.doOCR(contentWriter, tempImagePath, null, "tesseract");
            log.info("OCR time consuming: {}", timeInterval.intervalMs());
            return contentWriter.toString();
        } catch (IOException e) {
            log.error("OCR occurred error: {}", e.getMessage());
        } finally {
            // 删除临时文件
            FileUtil.del(tempImagePath);
        }
        return "";
    }
}
