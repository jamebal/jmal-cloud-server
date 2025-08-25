package com.jmal.clouddisk.ocr;

import java.io.Writer;

public interface IOcrService {

    /**
     * 执行OCR识别
     * @param writer 用于写入识别结果的Writer
     * @param imagePath 图片路径
     * @param tempImagePath 临时图片路径, 用于存放预处理后的图片
     */
    void doOCR(Writer writer, String imagePath, String tempImagePath);

}
