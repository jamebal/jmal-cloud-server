package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.media.VideoInfoDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author jmal
 * @Description 文件简介
 * @Date 2020/11/12 1:54 下午
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileIntroVO extends FileBase implements Reflective {
    private String userId;
    private String username;
    /***
     * 文件路径(根路径为"/")
     */
    private String path;
    /***
     * updateDate 距离现在的时间
     */
    private Long agoTime;
    /***
     * 显示大小
     */
    private String showSize;
    /***
     * 文件后缀名
     */
    private String suffix;
    /***
     * 是否收藏
     */
    private Boolean isFavorite;
    /***
     * 是否分享
     */
    private Boolean isShare;
    /**
     * oss目录名称
     */
    private String ossFolder;
    /**
     * oss平台类型
     */
    private String ossPlatform;
    /***
     * 是否为私密链接
     */
    private Boolean isPrivacy;

    private String contentText;

    private Boolean shareBase;
    private Boolean subShare;
    /***
     * 分享有效期
     */
    private Long expiresAt;
    /**
     * music
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
    private String mediaCover;
    /**
     * m3u8文件路径(相对路径)
     */
    private String m3u8;
    /***
     * 图片的宽度
     */
    private String w;
    /***
     * 图片的高度
     */
    private String h;
    /***
     * 封面
     */
    private String cover;
    /***
     * 是否显示封面, 缩略图保存在content中
     */
    private Boolean showCover;
    /**
     * 挂载的文件id
     */
    private String mountFileId;
    private List<Tag> tags;
    /**
     * 备注, 主要用于全文检索
     */
    private String remark;
    /**
     * 操作权限
     */
    private List<OperationPermission> operationPermissionList;
    /**
     * 文件的etag
     */
    private String etag;
}
