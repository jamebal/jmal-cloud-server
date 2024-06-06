package com.jmal.clouddisk.video;

import lombok.Data;

@Data
public class VideoInfoDO {
    private Integer width;
    private Integer height;
    private String bitrate;
    private String format;
    private String duration;
}
