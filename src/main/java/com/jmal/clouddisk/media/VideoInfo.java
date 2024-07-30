package com.jmal.clouddisk.media;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class VideoInfo {
    private Integer width;
    private Integer height;
    private Integer bitrate;
    private String format;
    private Double frameRate;
    private Integer duration;
    private String covertPath;
    /**
     * 视频旋转角度
     */
    private Integer rotation;

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

    public VideoInfo() {
        this.width = 0;
        this.height = 0;
        this.frameRate = 0.0;
        this.format = "";
        this.bitrate = 0;
        this.duration = 0;
    }

    public VideoInfo(String videoPath, int width, int height, String format, int bitrate, int duration, int rotation, double frameRate) {
        if (rotation == 90 || rotation == 270) {
            this.width = height;
            this.height = width;
        } else {
            this.width = width;
            this.height = height;
        }
        this.format = format;
        this.bitrate = bitrate;
        this.duration = duration;
        this.frameRate = frameRate;
        this.rotation = rotation;
        log.debug("\r\nvideoPath: {}, width: {}, height: {}, format: {}, bitrate: {}, duration: {}", videoPath, width, height, format, bitrate, duration);
    }

    public VideoInfoDO toVideoInfoDO() {
        VideoInfoDO videoInfoDO = new VideoInfoDO();
        if (this.bitrate > 0) {
            videoInfoDO.setBitrateNum(this.bitrate);
            videoInfoDO.setBitrate(VideoInfoUtil.convertBitrateToReadableFormat(this.bitrate));
        }
        if (StrUtil.isNotBlank(this.format)) {
            videoInfoDO.setFormat(this.format);
        }
        if (this.duration > 0) {
            videoInfoDO.setDurationNum(this.duration);
            videoInfoDO.setDuration(VideoInfoUtil.formatTimestamp(this.duration, false));
        }
        if (this.height > 0 && this.width > 0) {
            videoInfoDO.setHeight(this.height);
            videoInfoDO.setWidth(this.width);
        }
        if (this.frameRate > 0) {
            videoInfoDO.setFrameRate(this.frameRate);
        }
        return videoInfoDO;
    }

}
