package com.jmal.clouddisk.lucene;

import lombok.Getter;

/**
 * 任务类型
 */
@Getter
public enum TaskType {
    /**
     * OCR识别
     */
    OCR("OCR识别"),
    /**
     * 视频转码
     */
    TRANSCODE_VIDEO("视频转码");

    private final String type;

    TaskType(String type) {
        this.type = type;
    }
}
