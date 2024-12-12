package com.jmal.clouddisk.ocr;

import cn.hutool.core.date.TimeInterval;
import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import io.github.mymonstercat.ocr.config.ParamConfig;

public class OcrTest {

    public static void main(String[] args) {

        ParamConfig paramConfig = ParamConfig.getDefaultConfig();
        paramConfig.setDoAngle(true);
        paramConfig.setMostAngle(true);
        paramConfig.setPadding(0);
        paramConfig.setMaxSideLen(960);
        InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
        // 开始识别
        TimeInterval interval = new TimeInterval();
        OcrResult ocrResult = engine.runOcr("/Users/jmal/Downloads/67514d9a-f5fe42a2-2f0280aa_temp_ocr.png", paramConfig);
        System.out.println("识别耗时：" + interval.intervalMs() + "ms");
        System.out.println(ocrResult.getStrRes());
    }
}

