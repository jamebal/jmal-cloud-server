package com.jmal.clouddisk.ocr;

import lombok.Getter;

@Getter
public enum OcrEngine {
    OCR_LITE_ONNX("ocrLiteOnnx"),
    TESSERACT("tesseract");

    private final String ocrEngineName;

    OcrEngine(String ocrEngineName) {
        this.ocrEngineName = ocrEngineName;
    }
}
