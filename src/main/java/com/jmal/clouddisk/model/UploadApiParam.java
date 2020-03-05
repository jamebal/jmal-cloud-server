package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * @Description UploadApiParam
 * @Author jmal
 * @Date 2020-01-27 14:50
 * @blame jmal
 */
@Data
public class UploadApiParam {
    /***
     * 当前是第几个分片
     */
    int chunkNumber;
    /***
     * 分片大小
     */
    int chunkSize;
    /***
     * 当前分片大小
     */
    int currentChunkSize;
    /***
     * 文件总大小
     */
    long totalSize;
    /***
     * 文件唯一表示MD5
     */
    String identifier;
    /***
     * 文件或文件夹名
     */
    String filename;
    /***
     * 相对路径,上传文件夹是会用到
     */
    String relativePath;
    /***
     * 总分片大小
     */
    int totalChunks;
    MultipartFile file;

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

    /***
     * 当前目录,用户的网盘目录,如果为空则为"/"
     */
    String currentDirectory;

    /***
     * 当前目录,用户的实际磁盘目录
     */
    String currentAbsoluteDirectory;

    /***
     * 是否是文件夹
     */
    Boolean isFolder;
    /***
     * 当isFolder=true时 生效,是否是根目录
     */
    Boolean isRoot;
    /***
     * 当isFolder=true时 生效,folderPath为文件夹路径
     */
    String folderPath;
    String suffix;
    String contentType;

    String contentText;

    Integer pageIndex;
    Integer pageSize;
}
