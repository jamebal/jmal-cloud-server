package com.jmal.clouddisk.oss;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * @author jmal
 * @Description oss 存储接口
 * @date 2023/3/29 11:48
 */
public interface IOssStorageService {

    PlatformOSS getPlatform();

    /**
     * 获取单个文件夹信息
     * @param objectName 文件夹名称
     * @return FileInfo
     */
    FileInfo getFileInfo(String objectName);

    boolean delete(String objectName);

    AbstractOssObject getObject(String objectName);

    boolean mkdir(String ossPath, String objectName);

    boolean write(InputStream inputStream, String ossPath, String objectName);

    /**
     * 列出当前文件夹下的所有文件和文件夹
     * @param objectName 当前文件夹名称
     * @return String[]
     */
    String[] list(String objectName);

    List<FileInfo> getFileInfoList(String objectName);

    void uploadFile(Path tempFileAbsolutePath, String objectName);

    void mkdir(String objectName);
}
