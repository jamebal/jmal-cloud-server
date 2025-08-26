package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.Music;
import com.jmal.clouddisk.model.Tag;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 文件扩展属性 - 存储不需要查询的字段
 */
@Getter
@Setter
public class OtherProperties extends ExtendedProperties {

    private Music music;
    private ExifInfo exif;
    private VideoInfoDO video;
    private String mediaCover;
    private String m3u8;
    private String vtt;
    private List<Tag> tags;
    private Boolean showCover;
    private String remark;
    private String mountFileId;
    private Integer delete;

}
