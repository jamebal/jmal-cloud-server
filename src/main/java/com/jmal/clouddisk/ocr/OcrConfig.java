package com.jmal.clouddisk.ocr;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * OCR配置
 */
@Data
@Document(collection = "ocrConfig")
@Valid
@Schema
public class OcrConfig {

    @Schema(description = "是否启用orc, 默认开启")
    private Boolean enable;

    @Max(value = 8, message = "最大任务数不能超过8")
    @Min(value = 1, message = "最大任务数不能小于1")
    @Schema(description = "最大任务数, 最多同时处理的ocr任务数, 默认为1")
    private Integer maxTasks;

    @Schema(description = "ocr引擎, 默认tesseract")
    private String ocrEngine;

    public Boolean getEnable() {
        if (enable == null)
            return true;
        return enable;
    }

    public String getOcrEngine() {
        if (ocrEngine == null)
            return OcrEngine.TESSERACT.getOcrEngineName();
        return ocrEngine;
    }

    public void setOcrEngine(String ocrEngine) {
        if (ocrEngine == null)
            return;
        if (!OcrEngine.TESSERACT.getOcrEngineName().equals(ocrEngine) && !OcrEngine.OCR_LITE_ONNX.getOcrEngineName().equals(ocrEngine))
            return;
        this.ocrEngine = ocrEngine;
    }

    public Integer getMaxTasks() {
        if (maxTasks == null)
            return 1;
        return maxTasks;
    }
}
