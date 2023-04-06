package com.jmal.clouddisk.oss;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * @author jmal
 * @Description oss 存储接口
 * @date 2023/3/29 11:48
 */
public interface IOssService {

    PlatformOSS getPlatform();

    /**
     * Webdav 获取FileInfo
     * @param objectName object key
     * @return FileInfo
     */
    FileInfo getFileInfo(String objectName);

    /**
     * Webdav 删除文件
     * @param objectName object key
     * @return 是否删除成功
     */
    boolean delete(String objectName);

    /**
     * Webdav 创建文件夹
     * @param objectName object key
     * @return 是否创建成功
     */
    boolean mkdir(String objectName);

    /**
     * Webdav 上传文件
     * @param inputStream 文件输入流
     * @param ossPath     ossPath
     * @param objectName  object key
     * @return 是否上传成功
     */
    boolean write(InputStream inputStream, String ossPath, String objectName);

    /**
     * Webdav 列出当前文件夹下的所有文件和文件夹
     * @param objectName object key
     * @return 文件名称列表(包含文件夹)
     */
    String[] list(String objectName);

    /**
     * Webdav 获取文件对象(有时是下载)
     * @param objectName object key
     * @return AbstractOssObject
     */
    AbstractOssObject getObject(String objectName);


    /**
     * 获取 AbstractOssObject
     * @param objectName object key
     * @return AbstractOssObject
     */
    AbstractOssObject getAbstractOssObject(String objectName);

    /**
     * 删除文件
     * @param objectName object key
     */
    boolean deleteObject(String objectName);

    /**
     * 删除文件夹
     * @param objectName object key
     */
    boolean deleteDir(String objectName);

    /**
     * 获取objectName下的所有文件夹和文件
     * @param objectName object key
     * @return List<FileInfo>
     */
    List<FileInfo> getFileInfoList(String objectName);

    /**
     * 创建文件夹
     * @param objectName object key
     */
    FileInfo newFolder(String objectName);

    /**
     * 上传文件到 OSS
     * @param tempFileAbsolutePath 临时文件的绝对路径
     * @param objectName object key
     */
    void uploadFile(Path tempFileAbsolutePath, String objectName);

    /**
     * 检查Bucket是否存在，并且验证配置是否可用，用于创建OSS配置时使用
     */
    boolean doesBucketExist();

    /**
     * 关闭需要关闭的一切
     */
    void close();
}
