package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.impl.CommonFileService;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Data
@NoArgsConstructor
@Document(collection = CommonFileService.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "name_1", def = "{'name': 1}"),
        @CompoundIndex(name = "size_1", def = "{'size': 1}"),
        @CompoundIndex(name = "updateDate_1", def = "{'updateDate': 1}"),
        @CompoundIndex(name = "path_name", def = "{'path': 1, 'name': 1}"),
        @CompoundIndex(name = "user_md5_path", def = "{'userId': 1,'md5': 1, 'path': 1}"),
        @CompoundIndex(name = "user_path", def = "{'userId': 1, 'path': 1}"),
        @CompoundIndex(name = "user_path_name", def = "{'userId': 1, 'path': 1, 'name': 1}"),
        @CompoundIndex(name = "user_isFolder_path", def = "{'userId': 1, 'isFolder': 1, 'path': 1}"),
        @CompoundIndex(name = "user_isFolder_path_name", def = "{'userId': 1, 'isFolder': 1, 'path': 1, 'name': 1}"),
        @CompoundIndex(name = "user_isFolder", def = "{'userId': 1, 'isFolder': 1}"),
        @CompoundIndex(name = "user_isFavorite", def = "{'userId': 1, 'isFavorite': 1}"),
        @CompoundIndex(name = "user_contentType", def = "{'userId': 1, 'contentType': 1}"),
        @CompoundIndex(name = "doc_tags", def = "{'tags.tagId': 1}"),
        @CompoundIndex(name = "process_marked_folders", def = "{ 'needsEtagUpdate': 1, 'isFolder': 1, 'lastEtagUpdateRequestAt': 1 }"),
})
public class FileDocument extends FileBase implements Reflective {
    private String userId;
    private String username;
    /***
     * 文件头像/缩略图
     */
    private String avatar;
    /***
     * 文件路径(根路径为"/")
     */
    private String path;
    private Integer childrenCount;
    /***
     * updateDate 距离现在的时间
     */
    private Long agoTime;
    /***
     * 图片的宽度
     */
    private String w;
    /***
     * 图片的高度
     */
    private String h;
    /***
     * 文件内容
     */
    private byte[] content;

    /***
     * 文件内容
     */
    private String contentText;
    /**
     * 文件编码
     */
    private String decoder;
    /***
     * html内容
     */
    private String html;
    /***
     * 文件后缀名
     */
    private String suffix;
    /***
     * 是否收藏
     */
    private Boolean isFavorite;
    /**
     * oss平台类型
     */
    private String ossPlatform;
    /**
     * oss目录名称
     */
    private String ossFolder;
    /***
     * 是否公共文件
     */
    private Boolean isPublic;
    /***
     * 是否分享
     */
    private Boolean isShare;
    /***
     * 是否分享
     */
    private Boolean isPrivacy;
    private String extractionCode;
    /***
     * shareId
     */
    private String shareId;
    /***
     * shareBase
     */
    private Boolean shareBase;
    private Boolean subShare;
    /***
     * 分享有效期
     */
    private Long expiresAt;
    /***
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
     * 媒体封面
     */
    private Boolean mediaCover;
    /**
     * m3u8文件路径(相对路径)
     */
    private String m3u8;
    /**
     * vtt文件路径(相对路径)
     */
    private String vtt;
    /***
     * 是否发布，适用于文档类型
     */
    private Boolean release;
    /***
     * 是否为独立页，适用于文档类型
     */
    private Boolean alonePage;
    /***
     * 独立页排序
     */
    private Integer pageSort;
    /***
     * 是否有草稿(FileDocument对象转String)
     */
    private String draft;
    /***
     * 封面
     */
    private String cover;
    /***
     * 缩略名
     */
    private String slug;
    /***
     * 分类Id集合
     */
    private List<String> categoryIds;
    /***
     * 标签Id集合
     */
    private List<String> tagIds;

    /**
     * 标签集合
     */
    private List<Tag> tags;

    /**
     * 挂载的文件id
     */
    private String mountFileId;

    /**
     * 删除标记 0:未删除 1:已删除
     */
    private Integer delete;

    private Boolean move;

    /**
     * 是否显示封面, 缩略图保存在content中
     */
    private Boolean showCover;

    /**
     * 备注, 主要用于全文检索
     */
    private String remark;

    /**
     * MONGO_INDEX_FIELD
     */
    private Integer index;

    private Integer transcodeVideo;

    /**
     * 操作权限
     */
    private List<OperationPermission> operationPermissionList;

    /**
     * 文件的 ETag
     */
    private String etag;

    /**
     * 重试时间点
     */
    private Instant retryAt;

    /**
     * ETag 更新失败次数
     */
    private Integer etagUpdateFailedAttempts;

    /**
     * 是否需要更新 ETag
     */
    private Boolean needsEtagUpdate;

    /**
     * 最后Etag更新请求时间
     */
    private Instant lastEtagUpdateRequestAt;

    /**
     * 最后Etag更新错误
     */
    private String lastEtagUpdateError;

    /**
     * AI生成的文件摘要
     */
    private String summary;

    /**
     * 临时字段，不存数据库，表示是否有草稿
     */
    @Transient
    private Boolean hasDraft;

    @Transient
    private InputStream inputStream;

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

    public Trash toTrash(boolean hidden, boolean move) {
        Trash trash = new Trash();
        trash.setId(this.getId());
        trash.setName(this.getName());
        trash.setPath(this.getPath());
        trash.setUserId(this.getUserId());
        trash.setH(this.getH());
        trash.setW(this.getW());
        trash.setSuffix(this.getSuffix());
        trash.setIsFolder(this.getIsFolder());
        trash.setContent(this.getContent());
        trash.setExif(this.getExif());
        trash.setMusic(this.getMusic());
        trash.setVideo(this.getVideo());
        trash.setContentType(this.getContentType());
        trash.setSize(this.getSize());
        trash.setUploadDate(this.getUploadDate());
        trash.setUpdateDate(this.getUpdateDate());
        trash.setMd5(this.getMd5());
        trash.setM3u8(this.getM3u8());
        trash.setVtt(this.getVtt());
        trash.setHidden(hidden);
        trash.setMove(move);
        return trash;
    }

    public void setMusicNull() {
        this.music = null;
    }

    public void setMusicInfo(MusicInfo music) {
        if (music != null) {
            this.music = new Music(music);
        }
    }

}
