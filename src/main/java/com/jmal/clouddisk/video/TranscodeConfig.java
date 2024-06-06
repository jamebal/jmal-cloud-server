package com.jmal.clouddisk.video;

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

    @Max(value = 1000000, message = "码率不能超过 1000000 kbps")
    @Min(value = 100, message = "码率不能低于 100 kbps")
    @Schema(description = "转码后的视频码率(kbps), 默认 2500 kbps, 小于该值则不转码")
    private Integer bitrate;

    @Max(value = 10000, message = "高度不能超过 10000")
    @Min(value = 100, message = "高度不能低于 100")
    @Schema(description = "转码后的视频高度(视频宽度, 默认随高度等比例缩放), 默认 720, 小于该值则不转码")
    private Integer height;

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
}
