package com.jmal.clouddisk.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 转码配置
 */
@Data
@Document(collection = "transcodeConfig")
@Valid
@Schema
public class TranscodeConfig {

    @Schema(description = "是否启用转码, 默认开启")
    private Boolean enable;

    @Max(value = 8, message = "最大任务数不能超过8")
    @Min(value = 1, message = "最大任务数不能小于1")
    @Schema(description = "最大任务数, 最多同时处理的转码任务数, 默认为1")
    private Integer maxThreads;

    @Max(value = 1000000, message = "码率不能超过 1000000 kbps")
    @Min(value = 100, message = "码率不能低于 100 kbps")
    @Schema(description = "转码条件: 视频码率(kbps), 默认 2500 kbps, 小于该值则不转码")
    private Integer bitrateCond;

    @Max(value = 10000, message = "高度不能超过 10000")
    @Min(value = 100, message = "高度不能低于 100")
    @Schema(description = "转码条件: 视频高度(视频宽度, 默认随高度等比例缩放), 默认 720, 小于该值则不转码")
    private Integer heightCond;

    @Max(value = 120, message = "目标帧率不能超过 120")
    @Min(value = 1, message = "目标帧率不能低于 1")
    @Schema(description = "转码条件: 视频帧率, 默认 30 fps, 小于该值则不转码")
    private Integer frameRateCond;

    @Max(value = 1000000, message = "码率不能超过 1000000 kbps")
    @Min(value = 100, message = "码率不能低于 100 kbps")
    @Schema(description = "转码后的视频码率(kbps), 默认 2500 kbps")
    private Integer bitrate;

    @Max(value = 10000, message = "高度不能超过 10000")
    @Min(value = 100, message = "高度不能低于 100")
    @Schema(description = "转码后的视频高度(视频宽度, 默认随高度等比例缩放), 默认 720")
    private Integer height;

    @Max(value = 120, message = "目标帧率不能超过 120")
    @Min(value = 1, message = "目标帧率不能低于 1")
    @Schema(description = "转码后的视频帧率, 默认 30 fps")
    private Double frameRate;

    @Max(value = 256, message = "缩略图数量超过 256")
    @Min(value = 1, message = "缩略图数量不能低于 1")
    @Schema(description = "vtt缩略图数量, 默认 60 张")
    private Integer vttThumbnailCount;

    @Schema(description = "是否重新转码, 转码参数变化后对已经转码过的视频重新转码, 默认开启")
    private Boolean isReTranscode;

    public Boolean getEnable() {
        if (enable == null)
            return true;
        return enable;
    }

    public Integer getHeight() {
        if (height == null)
            return 720;
        return height;
    }

    public Integer getBitrate() {
        if (bitrate == null)
            return 2500;
        return bitrate;
    }

    public Double getFrameRate() {
        if (frameRate == null)
            return 30.0;
        return frameRate;
    }

    public Integer getMaxThreads() {
        if (maxThreads == null)
            return 1;
        return maxThreads;
    }

    public Integer getBitrateCond() {
        if (bitrateCond == null)
            return 2500;
        return bitrateCond;
    }

    public Integer getHeightCond() {
        if (heightCond == null)
            return 720;
        return heightCond;
    }

    public Integer getFrameRateCond() {
        if (frameRateCond == null)
            return 30;
        return frameRateCond;
    }

    public Integer getVttThumbnailCount() {
        if (vttThumbnailCount == null)
            return 60;
        return vttThumbnailCount;
    }

    public Boolean getIsReTranscode() {
        if (isReTranscode == null)
            return true;
        return isReTranscode;
    }
}
