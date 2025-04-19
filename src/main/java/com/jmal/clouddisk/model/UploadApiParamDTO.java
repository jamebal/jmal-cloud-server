package com.jmal.clouddisk.model;

import cn.hutool.core.util.URLUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Description UploadApiParam
 * @Author jmal
 * @Date 2020-01-27 14:50
 * @author jmal
 */
@Data
public class UploadApiParamDTO {
    /***
     * 当前是第几个分片
     */
    Integer chunkNumber;
    /***
     * 分片大小
     */
    Integer chunkSize;
    /***
     * 当前分片大小
     */
    Integer currentChunkSize;
    /***
     * 文件总大小
     */
    Long totalSize;
    /***
     * 文件唯一标识MD5
     */
    String identifier;
    /***
     * 文件或文件夹名
     */
    String filename;
    /**
     * 文件最后修改时间
     */
    Long lastModified;
    /***
     * 文件Id
     */
    String fileId;
    /***
     * 相对路径,上传文件夹是会用到
     */
    String relativePath;
    /***
     * 总分片大小
     */
    Integer totalChunks;
    MultipartFile file;

    List<String> filenames;

    InputStream inputStream;

    /***
     * 所有的文件都存在这
     */
    String rootPath;

    /***
     * 用户名
     */
    String username;
    String userId;

    String shareId;

    /***
     * 当前目录,用户的网盘目录,如果为空则为"/"
     */
    String currentDirectory;

    /**
     * 目录fileId
     */
    String folder;
    Map<String, Object> props;
    /**
     * 文件id
     */
    String mountFileId;

    /***
     * 当前目录,用户的实际磁盘目录
     */
    String currentAbsoluteDirectory;

    /***
     * 是否是文件夹
     */
    Boolean isFolder;
    /**
     * 是否只显示文件夹
     */
    Boolean justShowFolder;
    /**
     * path后面是否加上文件名
     */
    Boolean pathAttachFileName;
    /***
     * 当isFolder=true时 生效,是否是根目录
     */
    Boolean isRoot;
    /***
     * 当isFolder=true时 生效,folderPath为文件夹路径
     */
    String folderPath;
    /***
     * 文件名后缀
     */
    String suffix;
    /***
     * 文件类型
     */
    String contentType;
    /***
     * 要查询的文件类型
     */
    String queryFileType;
    /***
     * 文件文本
     */
    String contentText;
    /***
     * 要查询的页数
     */
    Integer pageIndex;
    /***
     * 要查询的每页条数
     */
    Integer pageSize;
    /***
     * 要查询的排序参数
     */
    String sortableProp;
    /***
     * 要查询的排序顺序
     */
    String order;
    /**
     * 是否显示文件夹大小
     */
    Boolean showFolderSize;
    /***
     * 是否为草稿
     */
    Boolean isDraft;
    /***
     * 是否已发布
     */
    Boolean isRelease;
    /***
     * 是否收藏
     */
    Boolean isFavorite;
    /**
     * tagId
     */
    String tagId;
    /**
     * 是否在回收站
     */
    Boolean isTrash;
    /***
     * 封面
     */
    String cover;
    /***
     * 缩略名
     */
    String slug;
    /***
     * 分类Id集合
     */
    String[] categoryIds;
    /***
     * 标签名称集合
     */
    String[] tagNames;
    /**
     * 操作权限
     */
    List<OperationPermission> operationPermissionList;
    /**
     * 隐藏挂载的文件
     */
    Boolean hideMountFile;

    /**
     * 是否覆盖, 用于移动或复制文件
     */
    Boolean isOverride;

    /**
     * 移动或复制文件的目标路径
     */
    String targetPath;

    /**
     * 修改时间范围开始
     */
    Long queryModifyStart;

    /**
     * 修改时间范围结束
     */
    Long queryModifyEnd;

    /**
     * 文件大小范围最小
     */
    Long querySizeMin;

    /**
     * 文件大小范围最大
     */
    Long querySizeMax;

    /**
     * 是否搜索挂载盘
     */
    Boolean searchMount;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;

    public String getFilename() {
        return URLUtil.decode(filename);
    }

    public String getRelativePath() {
        if (File.separator.equals("\\")) {
            // Windows 系统
            relativePath = relativePath.replace("/", "\\");
        }
        return URLUtil.decode(relativePath);
    }

    public String getCurrentDirectory() {
        if (currentDirectory == null || "undefined".equals(currentDirectory)) {
            return File.separator;
        }
        if (File.separator.equals("\\")) {
            // Windows 系统
            currentDirectory = currentDirectory.replace("/", "\\");
        }
        return URLUtil.decode(currentDirectory);
    }

    public String getFolderPath() {
        if (folderPath == null || "undefined".equals(folderPath)) {
            return "";
        }
        if (File.separator.equals("\\")) {
            // Windows 系统
            folderPath = folderPath.replace("/", "\\");
        }
        return URLUtil.decode(folderPath);
    }

}
