package com.jmal.clouddisk.ocr;

public interface IOcrService {

    /**
     * 执行OCR识别
     * @param imagePath 图片路径
     * @param tempImagePath 临时图片路径, 用于存放预处理后的图片
     * @return 识别结果
     */
    String doOCR(String imagePath, String tempImagePath);

}
