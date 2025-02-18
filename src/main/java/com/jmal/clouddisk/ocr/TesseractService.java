package com.jmal.clouddisk.ocr;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.File;


@Service("tesseract")
@RequiredArgsConstructor
@Slf4j
@Primary
public class TesseractService implements IOcrService {

    private final ThreadLocal<Tesseract> tesseractThreadLocal;

    private final CommonOcrService commonOcrService;

    @Override
    public String doOCR(String imagePath, String tempImagePath) {
        if (CharSequenceUtil.isBlank(imagePath)) {
            return "";
        }
        try {
            if (CharSequenceUtil.isBlank(tempImagePath)) {
                tempImagePath = commonOcrService.generateOrcTempImagePath(null);
            }
            // 预处理后的图片
            String preprocessedOCRImage = CommonOcrService.getPreprocessedOCRImage(imagePath, tempImagePath);
            if (CharSequenceUtil.isBlank(preprocessedOCRImage)) {
                return "";
            }
            File imageFile = new File(preprocessedOCRImage);
            ITesseract tesseract = tesseractThreadLocal.get();
            return tesseract.doOCR(imageFile);
        } catch (Exception e) {
            log.warn("Error while performing OCR: {}", e.getMessage(), e);
        } finally {
            FileUtil.del(tempImagePath);
        }
        return "";
    }

    @PreDestroy
    public void unload() {
        tesseractThreadLocal.remove();
    }
}
