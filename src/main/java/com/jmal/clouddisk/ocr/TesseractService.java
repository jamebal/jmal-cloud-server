package com.jmal.clouddisk.ocr;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;

import java.io.File;


@Service
@RequiredArgsConstructor
@Slf4j
public class TesseractService implements IOcrService {

    private final ThreadLocal<Tesseract> tesseractThreadLocal;

    private final CommonOcrService commonOcrService;

    public String doOCR(String imagePath, String tempImagePath) {
        try {
            if (StrUtil.isBlank(imagePath)) {
                return "";
            }
            if (StrUtil.isBlank(tempImagePath)) {
                tempImagePath = generateOrcTempImagePath(null);
            }
            // 预处理后的图片
            String preprocessedOCRImage = CommonOcrService.getPreprocessedOCRImage(imagePath, tempImagePath);
            if (StrUtil.isBlank(preprocessedOCRImage)) {
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

    public String generateOrcTempImagePath(String username) {
        return commonOcrService.generateOrcTempImagePath(username);
    }
}
