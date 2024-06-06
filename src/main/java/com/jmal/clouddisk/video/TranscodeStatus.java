package com.jmal.clouddisk.video;

import lombok.Getter;

/**
 * 转码状态
 */
@Getter
public enum TranscodeStatus {
    /**
     * 待转码
     */
    NOT_TRANSCODE(0),
    /**
     * 正在进行转码
     */
    TRANSCODING(1),
    /**
     * 已完成转码
     */
    TRANSCENDED(2);

    private final int status;

    TranscodeStatus(int status) {
        this.status = status;
    }

}
