package com.jmal.clouddisk.oss;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author jmal
 * @Description oss 存储接口
 * @date 2023/3/29 11:48
 */
public interface IOssService {

    PlatformOSS getPlatform();

    /**
     * 是否启用 S3 代理功能, 启用后上传下载流量会通过jmalcloud服务中转, 默认关闭
     */
    Boolean getProxyEnabled();

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
     * @param inputStream 文件输入流, 使用后需要关闭
     * @param ossPath     ossPath
     * @param objectName  object key
     * @return 是否上传成功
     */
    boolean write(InputStream inputStream, String ossPath, String objectName);

    /**
     * Webdav 上传文件
     *
     * @param inputStream 文件输入流, 使用后需要关闭
     * @param ossPath     ossPath
     * @param objectName  object key
     * @param size       文件大小
     * @return 是否上传成功
     */
    boolean write(InputStream inputStream, String ossPath, String objectName, long size);

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
    AbstractOssObject getObjectCache(String objectName);


    /**
     * 获取 AbstractOssObject
     * @param objectName object key
     * @return AbstractOssObject
     */
    AbstractOssObject getAbstractOssObject(String objectName);

    /**
     * 获取 AbstractOssObject
     * @param objectName object key
     * @param rangeStart rangeStart 分段
     * @param rangeEnd rangeEnd 分段
     * @return AbstractOssObject
     */
    AbstractOssObject getAbstractOssObject(String objectName, Long rangeStart, Long rangeEnd);

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
     * 列举所有包含指定前缀的所有文件
     * @param objectName objectName
     * @return objectNameList
     */
    List<FileInfo> getAllObjectsWithPrefix(String objectName);

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
    boolean uploadFile(InputStream inputStream, String objectName, long inputStreamLength);

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
     *
     * @param objectName objectName
     * @param uploadId   uploadId
     * @param fileTotalSize 文件总大小
     */
    void completeMultipartUpload(String objectName, String uploadId, Long fileTotalSize);

    void completeMultipartUploadWithParts(String objectName, String uploadId, List<PartInfo> partInfoList, Long fileTotalSize);

    /**
     * 获取缩略图, 指定目标图片宽度为 Width，高度等比缩放
     * @param objectName objectName
     * @param width      图片宽度
     */
    InputStream getThumbnail(String objectName, int width);

    /**
     * 生成预签名URL
     *
     * @param objectName objectName
     * @param expiryTime 过期时间(秒)
     * @param isDownload 是否直接下载
     * @return 预签名URL
     */
    String getPresignedObjectUrl(String objectName, int expiryTime, boolean isDownload);

    /**
     * 生成上传用的预签名URL（PUT）
     *
     * @param objectName  文件 key
     * @param contentType 文件的 Content-Type
     * @param expiryTime  过期时间(秒)
     * @return 预签名URL
     */
    String getPresignedPutUrl(String objectName, String contentType, int expiryTime);

    /**
     * 获取分片上传预签名URLs
     * @param objectName objectName
     * @param uploadId uploadId
     * @param totalParts 总分片数
     * @param expiryTime 过期时间(秒)
     * @return 分片号和预签名URL的映射表
     */
    Map<Integer, String> getPresignedUploadPartUrls(String objectName, String uploadId, int totalParts, int expiryTime);

    /**
     * 拷贝对象(相同Bucket之间拷贝)
     *
     * @param sourceKey      源objectName
     * @param destinationKey 目标objectName
     * @return 复制成功的objectName列表
     */
    List<String> copyObject(String sourceKey, String destinationKey);

    /**
     * 拷贝对象(不同Bucket之间拷贝)
     *
     * @param sourceBucketName      源Bucket
     * @param sourceKey             源objectName
     * @param destinationBucketName 目标Bucket
     * @param destinationKey        目标objectName
     * @return 复制成功的objectName列表
     */
    List<String> copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey);

    /**
     * 锁对象
     * @param objectName objectName
     */
    void lock(String objectName);

    /**
     * 解锁对象
     * @param objectName objectName
     */
    void unlock(String objectName);

    /**
     * 清除缓存 objectName 及以下的所有缓存
     * @param objectName objectName
     */
    void clearCache(String objectName);

    /**
     * 关闭需要关闭的一切
     */
    void close();

}
