package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.Music;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件扩展属性 - 存储不需要查询的字段
 */
@Getter
@Setter
public class OtherProperties extends ExtendedProperties {
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

}
