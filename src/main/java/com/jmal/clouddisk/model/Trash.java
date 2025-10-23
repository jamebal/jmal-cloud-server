package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.file.ExifInfo;
import com.jmal.clouddisk.model.file.FileBase;
import com.jmal.clouddisk.service.impl.CommonFileService;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Objects;

/**
 * Trash 文件模型
 *
 * @author jmal
 */
@Data
@Document(collection = CommonFileService.TRASH_COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "name_1", def = "{'name': 1}"),
        @CompoundIndex(name = "size_1", def = "{'size': 1}"),
        @CompoundIndex(name = "updateDate_1", def = "{'updateDate': 1}"),
        @CompoundIndex(name = "hidden_1", def = "{'hidden': 1}"),
})
public class Trash extends FileBase implements Reflective {
    private String userId;
    private String path;
    /**
     * 图片的宽度
     */
    private String w;
    /**
     * 图片的高度
     */
    private String h;
    /**
     * 文件内容
     */
    private byte[] content;
    /***
     * 文件后缀名
     */
    private String suffix;
    /**
     * 音乐
     */
    private Music music;
    /**
     * 照片exif信息
     */
    private ExifInfo exif;
    /**
     * 视频信息
     */
    private VideoInfoDO video;
    /**
     * m3u8文件路径(相对路径)
     */
    private String m3u8;
    /**
     * vtt文件路径(相对路径)
     */
    private String vtt;
    /**
     * 是否隐藏显示
     */
    private Boolean hidden;
    /**
     * 是否移动到回收站, 从原位置移动到jmalcloudTrashDir
     */
    private Boolean move;

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileBase other = (FileBase) obj;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }
}
