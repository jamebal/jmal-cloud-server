package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.Music;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件扩展属性 - 存储不需要查询的字段
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtherProperties {
    /***
     * 图片的宽度
     */
    private String w;
    /***
     * 图片的高度
     */
    private String h;
    private Music music;
    private ExifInfo exif;
    private VideoInfoDO video;
    private String mediaCover;
    private String m3u8;
    private String vtt;
    private Boolean showCover;
    private String remark;
    private String ossPlatform;

    public OtherProperties(FileDocument fileDocument) {
        this.w = fileDocument.getW();
        this.h = fileDocument.getH();
        this.music = fileDocument.getMusic();
        this.exif = fileDocument.getExif();
        this.video = fileDocument.getVideo();
        this.mediaCover = fileDocument.getMediaCover();
        this.m3u8 = fileDocument.getM3u8();
        this.vtt = fileDocument.getVtt();
        this.showCover = fileDocument.getShowCover();
        this.remark = fileDocument.getRemark();
        this.ossPlatform = fileDocument.getOssPlatform();
    }
}
