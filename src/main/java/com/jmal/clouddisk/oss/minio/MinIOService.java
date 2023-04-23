package com.jmal.clouddisk.oss.minio;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author jmal
 * @Description MinIOService
 * @date 2023/4/23 16:36
 */
@Slf4j
public class MinIOService implements IOssService {

    private final String bucketName;

    private final MinioClient ossClient;

    private final BaseOssService baseOssService;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public MinIOService(FileProperties fileProperties, OssConfigDTO ossConfigDTO) {
        String endpoint = ossConfigDTO.getEndpoint();
        String region = ossConfigDTO.getRegion();
        String accessKeyId = ossConfigDTO.getAccessKey();
        String accessKeySecret = ossConfigDTO.getSecretKey();
        this.bucketName = ossConfigDTO.getBucket();
        // 创建ossClient实例。
        this.ossClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKeyId, accessKeySecret)
                .region(region)
                .build();
        scheduledThreadPoolExecutor = ThreadUtil.createScheduledExecutor(1);
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor);
        log.info("{}配置加载成功, bucket: {}, username: {}, {}", getPlatform().getValue(), bucketName, ossConfigDTO.getUsername(), this.hashCode());
    }

    @Override
    public PlatformOSS getPlatform() {
        return PlatformOSS.MINIO;
    }

    @Override
    public FileInfo getFileInfo(String objectName) {
        return baseOssService.getFileInfo(objectName);
    }

    @Override
    public boolean delete(String objectName) {
        return baseOssService.delete(objectName);
    }

    @Override
    public boolean mkdir(String objectName) {
        return baseOssService.mkdir(objectName);
    }

    @Override
    public boolean write(InputStream inputStream, String ossPath, String objectName) {
        return baseOssService.writeTempFile(inputStream, ossPath, objectName);
    }

    @Override
    public String[] list(String objectName) {
        return baseOssService.getFileNameList(objectName).toArray(new String[0]);
    }

    @Override
    public AbstractOssObject getObjectCache(String objectName) {
        return baseOssService.getObject(objectName);
    }

    @Override
    public AbstractOssObject getAbstractOssObject(String objectName) {
        MinIOObject ossObject = null;
        try {
            StatObjectArgs statObjectArgs = StatObjectArgs.builder().bucket(bucketName).object(objectName).build();
            StatObjectResponse statObjectResponse = this.ossClient.statObject(statObjectArgs);
            GetObjectArgs objectArgs = GetObjectArgs.builder().bucket(bucketName).object(objectName).build();
            GetObjectResponse getObjectResponse = this.ossClient.getObject(objectArgs);
            ossObject = new MinIOObject(statObjectResponse, getObjectResponse);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return ossObject;
    }

    @Override
    public boolean deleteObject(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteObject", objectName);
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build();
            ossClient.removeObject(removeObjectArgs);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteDir(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteDir", objectName);
            ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).build();
            Iterable<Result<Item>> results = ossClient.listObjects(listObjectsArgs);
            for (Result<Item> result : results) {
                Item item = result.get();
                RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder().bucket(bucketName).object(item.objectName()).build();
                ossClient.removeObject(removeObjectArgs);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public List<FileInfo> getFileInfoListCache(String objectName) {
        return baseOssService.getFileInfoListCache(objectName);
    }

    @Override
    public List<FileInfo> getFileInfoList(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).delimiter("/").build();
            Iterable<Result<Item>> results = ossClient.listObjects(listObjectsArgs);
            for (Result<Item> result : results) {
                Item item = result.get();
                Date lastModified = Date.from(item.lastModified().toInstant());
                S3ObjectSummary s3ObjectSummary = new S3ObjectSummary(item.size(), item.objectName(), item.etag(), lastModified, bucketName);
                baseOssService.addFileInfoList(objectName, fileInfoList, s3ObjectSummary);
                if (item.isDir()) {
                    fileInfoList.add(baseOssService.newFileInfo(item.objectName()));
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return fileInfoList;
    }

    @Override
    public List<FileInfo> getAllObjectsWithPrefix(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).build();
            Iterable<Result<Item>> results = ossClient.listObjects(listObjectsArgs);
            for (Result<Item> result : results) {
                Item item = result.get();
                Date lastModified = Date.from(item.lastModified().toInstant());
                S3ObjectSummary s3ObjectSummary = new S3ObjectSummary(item.size(), item.objectName(), item.etag(), lastModified, bucketName);
                fileInfoList.add(baseOssService.getFileInfo(s3ObjectSummary));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return fileInfoList;
    }

    @Override
    public FileInfo newFolder(String objectName) {
        baseOssService.printOperation(getPlatform().getKey(), "mkdir", objectName);
        if (!objectName.endsWith("/")) {
            objectName = objectName + "/";
        }
        try {
            // 上传字符串
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(new byte[] {}), 0, -1).build();
            ossClient.putObject(putObjectArgs);
            return baseOssService.newFileInfo(objectName);
        } catch (Exception oe) {
            log.error(oe.getMessage(), oe);
        }
        return null;
    }

    @Override
    public void uploadFile(Path tempFileAbsolutePath, String objectName) {
        try {
            if (!PathUtil.exists(tempFileAbsolutePath, false)) {
                return;
            }
            File file = tempFileAbsolutePath.toFile();
            baseOssService.printOperation(getPlatform().getKey(), "upload", objectName);
            ObjectWriteResponse objectWriteResponse;
            try(InputStream inputStream = new FileInputStream(file)) {
                PutObjectArgs putObjectArgs = PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                inputStream, -1, tempFileAbsolutePath.toFile().length())
                        .contentType(FileContentTypeUtils.getContentType(FileUtil.getSuffix(file)))
                        .build();
                objectWriteResponse = ossClient.putObject(putObjectArgs);
            }
            if (objectWriteResponse != null && objectWriteResponse.etag() != null) {
                // 上传成功
                baseOssService.onUploadSuccess(objectName, tempFileAbsolutePath);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void uploadFile(InputStream inputStream, String objectName, long inputStreamLength) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "uploadFile inputStream", objectName);
            ObjectWriteResponse objectWriteResponse;
            PutObjectArgs putObjectArgs = PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                            inputStream, -1, inputStreamLength)
                    .contentType(FileContentTypeUtils.getContentType(FileUtil.getSuffix(objectName)))
                    .build();
            objectWriteResponse = ossClient.putObject(putObjectArgs);
            if (objectWriteResponse != null && objectWriteResponse.etag() != null) {
                // 上传成功
                baseOssService.onUploadSuccess(objectName, inputStreamLength);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public boolean doesBucketExist() {
        boolean exist = false;
        try {
            BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
            exist = this.ossClient.bucketExists(bucketExistsArgs);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return exist;
    }

    @Override
    public boolean doesObjectExist(String objectName) {
        StatObjectArgs statObjectArgs = StatObjectArgs.builder().bucket(bucketName).object(objectName).build();
        try {
            StatObjectResponse statObjectResponse = this.ossClient.statObject(statObjectArgs);
            if (statObjectResponse != null && !statObjectResponse.deleteMarker()) {
                return true;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public String getUploadId(String objectName) {
        return null;
    }

    @Override
    public String initiateMultipartUpload(String objectName) {
        return null;
    }

    @Override
    public CopyOnWriteArrayList<Integer> getListParts(String objectName, String uploadId) {
        return null;
    }

    @Override
    public boolean uploadPart(InputStream inputStream, String objectName, int partSize, int partNumber, String uploadId) {
        return false;
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {

    }

    @Override
    public void completeMultipartUpload(String objectName, String uploadId, Long fileTotalSize) {

    }

    @Override
    public void getThumbnail(String objectName, File file, int width) {

    }

    @Override
    public boolean copyObject(String sourceKey, String destinationKey) {
        return false;
    }

    @Override
    public boolean copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        return false;
    }

    @Override
    public void lock(String objectName) {

    }

    @Override
    public void unlock(String objectName) {

    }

    @Override
    public void clearCache(String objectName) {

    }

    @Override
    public void close() {
        log.info("platform: {}, bucketName: {} shutdown... {}", getPlatform().getValue(), bucketName, this.hashCode());
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }
}
