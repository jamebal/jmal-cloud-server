package com.jmal.clouddisk.ocr;

import cn.hutool.core.date.TimeInterval;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OcrController {

    private final  OcrService ocrService;

    @GetMapping("/public/ocr")
    public String performOcr(@RequestParam String imagePath) {
        TimeInterval timeInterval = new TimeInterval();
        timeInterval.start();
        String str = ocrService.doOCR(imagePath, null);
        System.out.println("耗时：" + timeInterval.intervalMs() + "ms");
        return str;
    }
}
