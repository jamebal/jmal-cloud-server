package com.jmal.clouddisk.media;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class VideoInfoDO {
    private Integer width;
    private Integer height;
    /**
     * 视频码率
     */
    private String bitrate;
    /**
     * 视频码率数值
     */
    private Integer bitrateNum;
    /**
     * 视频格式
     */
    private String format;
    /**
     * 视频帧率
     */
    private Double frameRate;
    /**
     * 视频时长
     */
    private String duration;
    /**
     * 视频时长数值(秒)
     */
    private Integer durationNum;

    /**
     * 转码后的视频高度
     */
    private Integer toHeight;
    /**
     * 转码后的视频码率
     */
    private Integer toBitrate;
    /**
     * 转码后的视频帧率
     */
    private Double toFrameRate;
}
