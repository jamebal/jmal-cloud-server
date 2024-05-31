package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.ocr.OcrService;
import com.jmal.clouddisk.util.FileContentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadPDFContentService {

    private final OcrService ocrService;

    public String read(File file) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(file))) {
            StringBuilder content = new StringBuilder();
            // 提取每一页的内容
            PDFTextStripper pdfStripper = new PDFTextStripper();
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                pdfStripper.setStartPage(pageNumber);
                pdfStripper.setEndPage(pageNumber);
                String text = pdfStripper.getText(document).trim();
                if (!text.isEmpty()) {
                    content.append(text);
                } else {
                    PDPage page = document.getPage(pageNumber - 1);
                    PDResources resources = page.getResources();
                    for (COSName xObjectName : resources.getXObjectNames()) {
                        PDXObject xObject = resources.getXObject(xObjectName);
                        if (xObject instanceof PDImageXObject image) {
                            BufferedImage bufferedImage = image.getImage();
                            // 将图像保存到临时文件
                            String tempImageFile = ocrService.generateOrcTempImagePath();
                            ImageIO.write(bufferedImage, "png", new File(tempImageFile));
                            try {
                                // 使用 Tesseract 进行 OCR 识别
                                String ocrResult = ocrService.doOCR(tempImageFile, null);
                                content.append(ocrResult);
                            } finally {
                                // 删除临时文件
                                FileUtil.del(tempImageFile);
                            }
                        }
                    }
                }
            }
            return content.toString();
        } catch (IOException e) {
            FileContentUtil.readFailed(file, e);
        }
        return null;
    }

}
