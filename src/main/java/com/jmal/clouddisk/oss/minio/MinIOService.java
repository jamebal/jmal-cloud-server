package com.jmal.clouddisk.oss.minio;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import com.google.common.collect.HashMultimap;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import io.minio.*;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.XmlParserException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description MinIOService
 * @date 2023/4/23 16:36
 */
@Slf4j
public class MinIOService implements IOssService {

    private final String bucketName;

    private final MinIoClient minIoClient;

    private final BaseOssService baseOssService;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public MinIOService(FileProperties fileProperties, OssConfigDTO ossConfigDTO) {
        String endpoint = ossConfigDTO.getEndpoint();
        String region = ossConfigDTO.getRegion();
        String accessKeyId = ossConfigDTO.getAccessKey();
        String accessKeySecret = ossConfigDTO.getSecretKey();
        this.bucketName = ossConfigDTO.getBucket();
        // 创建ossClient实例。
        this.minIoClient = new MinIoClient(MinioAsyncClient.builder()
                .endpoint(endpoint)
                .region(region)
                .credentials(accessKeyId, accessKeySecret)
                .build());
        scheduledThreadPoolExecutor = ThreadUtil.createScheduledExecutor(1);
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor);
        log.info("{}配置加载成功, bucket: {}, username: {}, {}", getPlatform().getValue(), bucketName, ossConfigDTO.getUsername(), this.hashCode());
        ThreadUtil.execute(this::getMultipartUploads);
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
            StatObjectResponse statObjectResponse = getStatObjectResponse(objectName).get();
            GetObjectArgs objectArgs = GetObjectArgs.builder().bucket(bucketName).object(objectName).build();
            GetObjectResponse getObjectResponse = this.minIoClient.getObject(objectArgs).get();
            ossObject = new MinIOObject(statObjectResponse, getObjectResponse);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return ossObject;
    }

    public CompletableFuture<StatObjectResponse> getStatObjectResponse(String objectName) throws InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException {
        StatObjectArgs statObjectArgs = StatObjectArgs.builder().bucket(bucketName).object(objectName).build();
        return this.minIoClient.statObject(statObjectArgs);
    }

    @Override
    public boolean deleteObject(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteObject", objectName);
            doDeleteObject(objectName);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    private void doDeleteObject(String objectName) throws InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException {
        RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build();
        this.minIoClient.removeObject(removeObjectArgs);
    }

    @Override
    public boolean deleteDir(String objectName) {
        boolean deleted = true;
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteDir", objectName);
            ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).recursive(true).includeVersions(true).build();
            Iterable<Result<Item>> results1 = this.minIoClient.listObjects(listObjectsArgs);
            List<DeleteObject> objects = new LinkedList<>();
            for (Result<Item> result : results1) {
                Item item = result.get();
                objects.add(new DeleteObject(item.objectName(), item.versionId()));
            }
            RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder().bucket(bucketName).objects(objects).build();
            Iterable<Result<DeleteError>> results = this.minIoClient.removeObjects(removeObjectsArgs);
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                deleted = false;
                log.error("Error in deleting object {}; {}", error.objectName(), error.message());
            }
        } catch (Exception e) {
            deleted = false;
            log.error(e.getMessage(), e);
        }
        return deleted;
    }

    @Override
    public List<FileInfo> getFileInfoListCache(String objectName) {
        return baseOssService.getFileInfoListCache(objectName);
    }

    @Override
    public List<FileInfo> getFileInfoList(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).delimiter("/").recursive(false).includeVersions(false).build();
            Iterable<Result<Item>> results = this.minIoClient.listObjects(listObjectsArgs);
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item == null) {
                    continue;
                }
                Date lastModified = null;
                if (BooleanUtil.isFalse(item.isDir())) {
                    lastModified = Date.from(item.lastModified().toInstant());
                }
                S3ObjectSummary s3ObjectSummary = new S3ObjectSummary(item.size(), item.objectName(), item.etag(), lastModified, bucketName);
                baseOssService.addFileInfoList(objectName, fileInfoList, s3ObjectSummary);
                if (item.isDir()) {
                    String key = item.objectName();
                    if (fileInfoList.stream().noneMatch(fileInfo -> key.equals(fileInfo.getKey()))) {
                        fileInfoList.add(baseOssService.newFileInfo(key));
                    }
                }
                fileInfoList = fileInfoList.parallelStream().peek(fileInfo -> {
                   if (fileInfo.getLastModified() == null) {
                       try {
                           Date date = getLastModified(fileInfo.getKey());
                           fileInfo.setLastModified(date);
                       } catch (Exception e) {
                           log.error(e.getMessage(), e);
                       }
                   }
                }).collect(Collectors.toList());
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
            ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).recursive(true).includeVersions(false).build();
            Iterable<Result<Item>> results = this.minIoClient.listObjects(listObjectsArgs);
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item == null) {
                    continue;
                }
                Date lastModified = Date.from(item.lastModified().toInstant());
                S3ObjectSummary s3ObjectSummary = new S3ObjectSummary(item.size(), item.objectName(), item.etag(), lastModified, bucketName);
                fileInfoList.add(baseOssService.getFileInfo(s3ObjectSummary));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return fileInfoList;
    }

    @Nullable
    private Date getLastModified(String objectName) throws InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException {
        try {
            StatObjectResponse statObjectResponse = getStatObjectResponse(objectName).get();
            return Date.from(statObjectResponse.lastModified().toInstant());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public FileInfo newFolder(String objectName) {
        baseOssService.printOperation(getPlatform().getKey(), "mkdir", objectName);
        if (!objectName.endsWith("/")) {
            objectName = objectName + "/";
        }
        try {
            // 上传字符串
            this.minIoClient.putObject(
                    PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                    new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .build());
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
            try (InputStream inputStream = new FileInputStream(file)) {
                PutObjectArgs putObjectArgs = getPutObjectArgs(inputStream, objectName, tempFileAbsolutePath.toFile().length());
                objectWriteResponse = this.minIoClient.putObject(putObjectArgs).get();
            }
            if (objectWriteResponse != null && objectWriteResponse.etag() != null) {
                baseOssService.onUploadSuccess(objectName, tempFileAbsolutePath);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void uploadFile(InputStream inputStream, String objectName, long inputStreamLength) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "uploadFile inputStream", objectName);
            ObjectWriteResponse objectWriteResponse;
            PutObjectArgs putObjectArgs = getPutObjectArgs(inputStream, objectName, inputStreamLength);
            objectWriteResponse = this.minIoClient.putObject(putObjectArgs).get();
            if (objectWriteResponse != null && objectWriteResponse.etag() != null) {
                // 上传成功
                baseOssService.onUploadSuccess(objectName, inputStreamLength);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private PutObjectArgs getPutObjectArgs(InputStream inputStream, String objectName, long inputStreamLength) {
        String contentType = baseOssService.getContentType(objectName);
        PutObjectArgs putObjectArgs;
        if (inputStreamLength >= 5 * 1024 * 1024) {
            putObjectArgs = PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                            inputStream, -1, inputStreamLength)
                    .contentType(contentType)
                    .build();
        } else {
            putObjectArgs = PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                            inputStream, inputStreamLength, -1)
                    .contentType(contentType)
                    .build();
        }
        return putObjectArgs;
    }

    @Override
    public boolean doesBucketExist() {
        boolean exist = false;
        try {
            BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
            exist = this.minIoClient.bucketExists(bucketExistsArgs).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return exist;
    }

    @Override
    public boolean doesObjectExist(String objectName) {
        try {
            StatObjectResponse statObjectResponse = getStatObjectResponse(objectName).get();
            if (statObjectResponse == null) {
                return false;
            }
            if (!statObjectResponse.deleteMarker()) {
                return true;
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            return false;
        }catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public String getUploadId(String objectName) {
        return baseOssService.getUploadId(objectName);
    }

    @Override
    public String initiateMultipartUpload(String objectName) {
        String uploadId = null;
        try {
            String contentType = baseOssService.getContentType(objectName);
            HashMultimap<String, String> headers = HashMultimap.create();
            headers.put("Content-Type", contentType);
            uploadId = this.minIoClient.initMultiPartUpload(bucketName, null, objectName, headers);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return uploadId;
    }

    @Override
    public CopyOnWriteArrayList<Integer> getListParts(String objectName, String uploadId) {
        return new CopyOnWriteArrayList<>(getPartETagList(objectName, uploadId).stream().map(Part::partNumber).toList());
    }

    private List<Part> getPartETagList(String objectName, String uploadId) {
        List<Part> listParts = new ArrayList<>();
        try {
            ListPartsResponse partResult = this.minIoClient.listMultipart(bucketName, null, objectName, 10000, 0, uploadId);
            return partResult.result().partList();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return listParts;
    }

    private void getMultipartUploads() {
    }

    @Override
    public boolean uploadPart(InputStream inputStream, String objectName, int partSize, int partNumber, String uploadId) {
        try {
            UploadPartResponse uploadPartResponse = this.minIoClient.uploadPart(bucketName, null, objectName, inputStream, partSize, uploadId, partNumber);
            if (uploadPartResponse != null && uploadId.equals(uploadPartResponse.uploadId())) {
                return true;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        try {
            this.minIoClient.abortMultipartUpload(bucketName, null, objectName, uploadId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void completeMultipartUpload(String objectName, String uploadId, Long fileTotalSize) {
        try {
            List<Part> partList = getPartETagList(objectName, uploadId);
            Part[] parts = partList.toArray(new Part[0]);
            this.minIoClient.mergeMultipartUpload(bucketName, null, objectName, uploadId, parts);
            baseOssService.onUploadSuccess(objectName, fileTotalSize);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void getThumbnail(String objectName, File file, int width) {
        try {
            DownloadObjectArgs downloadObjectArgs = DownloadObjectArgs.builder().bucket(bucketName).object(objectName).filename(file.getAbsolutePath()).build();
            this.minIoClient.downloadObject(downloadObjectArgs);
            byte[] bytes = FileInterceptor.imageCrop(file, "50", "256", null);
            FileUtil.writeBytes(bytes, file);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public boolean copyObject(String sourceKey, String destinationKey) {
        return copyObject(bucketName, sourceKey, bucketName, destinationKey);
    }

    @Override
    public boolean copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        baseOssService.setObjectNameLock(sourceBucketName);
        baseOssService.setObjectNameLock(destinationBucketName);
        try {
            if (sourceKey.endsWith("/")) {
                // 复制文件夹
                ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(sourceBucketName).prefix(sourceKey).recursive(true).build();
                Iterable<Result<Item>> results1 = this.minIoClient.listObjects(listObjectsArgs);
                for (Result<Item> result : results1) {
                    Item item = result.get();
                    copyObjectFile(sourceBucketName, sourceKey, destinationBucketName, item.objectName());
                }
            } else {
                // 复制文件
                copyObjectFile(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            baseOssService.removeObjectNameLock(sourceBucketName);
            baseOssService.removeObjectNameLock(destinationBucketName);
        }
        return false;
    }

    private void copyObjectFile(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "copyObject start" + "destinationKey: " + destinationKey, "sourceKey:" + sourceKey);
            this.minIoClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(destinationBucketName)
                            .object(destinationKey)
                            .source(CopySource.builder()
                                    .bucket(sourceBucketName)
                                    .object(sourceKey)
                                    .build()).build());
            baseOssService.printOperation(getPlatform().getKey(), "copyObject complete" + "destinationKey: " + destinationKey, "sourceKey:" + sourceKey);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void lock(String objectName) {
        baseOssService.setObjectNameLock(objectName);
    }

    @Override
    public void unlock(String objectName) {
        baseOssService.removeObjectNameLock(objectName);
    }

    @Override
    public void clearCache(String objectName) {
        baseOssService.clearCache(objectName);
    }

    @Override
    public void close() {
        log.info("platform: {}, bucketName: {} shutdown... {}", getPlatform().getValue(), bucketName, this.hashCode());
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }
}
