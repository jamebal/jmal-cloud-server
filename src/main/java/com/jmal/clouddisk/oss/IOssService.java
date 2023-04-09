package com.jmal.clouddisk.oss;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * 从缓存中获取objectName下的所有文件夹和文件
     * @param objectName object key
     * @return List<FileInfo>
     */
    List<FileInfo> getFileInfoListCache(String objectName);

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
     * 上传文件到 OSS
     * @param inputStream inputStream
     * @param objectName object key
     */
    void uploadFile(InputStream inputStream, String objectName, Integer inputStreamLength);

    /**
     * 检查Bucket是否存在，并且验证配置是否可用，用于创建OSS配置时使用
     */
    boolean doesBucketExist();

    /**
     * 判断对象是否存在
     */
    boolean doesObjectExist(String objectName);

    /**
     * 获取分片上传事件的唯一标识
     * @param objectName objectName
     * @return uploadId
     */
    String getUploadId(String objectName);

    /**
     * 初始化分片上传事件
     * @param objectName objectName
     * @return uploadId
     */
    String initiateMultipartUpload(String objectName);

    /**
     * 获取分片号列表
     * @param objectName objectName
     * @param uploadId uploadId
     * @return 分片号列表
     */
    CopyOnWriteArrayList<Integer> getListParts(String objectName, String uploadId);

    /**
     * 上传分片
     * @param inputStream 输入流
     * @param objectName objectName
     * @param partSize   分片大小
     * @param partNumber 分片编号
     * @param uploadId   uploadId
     * @return 分片是否上传成功
     */
    boolean uploadPart(InputStream inputStream, String objectName, int partSize, int partNumber, String uploadId);

    /**
     * 取消分片上传
     * @param objectName objectName
     * @param uploadId   uploadId
     */
    void abortMultipartUpload(String objectName, String uploadId);

    /**
     * 完成分片上传(合并分片)
     * @param objectName objectName
     * @param uploadId uploadId
     * @return uploadId
     */
    String completeMultipartUpload(String objectName, String uploadId);

    /**
     * 关闭需要关闭的一切
     */
    void close();
}
