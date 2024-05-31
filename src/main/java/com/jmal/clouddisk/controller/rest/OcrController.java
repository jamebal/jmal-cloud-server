package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.jmal.clouddisk.ocr.OcrService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "OCR")
@Slf4j
public class OcrController {

    private final OcrService ocrService;

    @GetMapping("/ocr")
    public String performOcr(@RequestParam String fileUrl) {
        String tempImagePath = ocrService.generateOrcTempImagePath();
        try {
            HttpUtil.downloadFile(fileUrl, tempImagePath);
            TimeInterval timeInterval = new TimeInterval();
            timeInterval.start();
            String str = ocrService.doOCR(tempImagePath, null);
            log.info("OCR time consuming: {}", timeInterval.intervalMs());
            return str;
        } finally {
            // 删除临时文件
            FileUtil.del(tempImagePath);
        }
    }
}
