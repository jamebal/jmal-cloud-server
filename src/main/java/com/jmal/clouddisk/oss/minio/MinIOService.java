package com.jmal.clouddisk.oss.minio;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import com.google.common.collect.HashMultimap;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
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
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor, ossConfigDTO);
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
        return getAbstractOssObject(objectName, null, null);
    }

    @Override
    public AbstractOssObject getAbstractOssObject(String objectName, Long rangeStart, Long rangeEnd) {
        MinIOObject ossObject = null;
        try {
            StatObjectResponse statObjectResponse = this.minIoClient.statObject(bucketName, objectName);
            GetObjectResponse getObjectResponse = this.minIoClient.getObject(bucketName, objectName, rangeStart, rangeEnd);
            ossObject = new MinIOObject(statObjectResponse, getObjectResponse, this);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return ossObject;
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

    private void doDeleteObject(String objectName) throws InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException, ServerException, ErrorResponseException, InvalidResponseException {
        this.minIoClient.removeObject(bucketName, objectName);
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
                fileInfoList = setLastModified(fileInfoList);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return fileInfoList;
    }

    @NotNull
    private List<FileInfo> setLastModified(List<FileInfo> fileInfoList) {
        fileInfoList = fileInfoList.parallelStream().peek(fileInfo -> {
            if (fileInfo.getLastModified() == null) {
                Date date = new Date(0);
                try {
                    date = getLastModified(fileInfo.getKey());
                } catch (Exception e) {
                    log.warn("getLastModified Failed: {}, key: {}", e.getMessage(), fileInfo.getKey());
                }
                fileInfo.setLastModified(date);
            }
        }).collect(Collectors.toList());
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

    private Date getLastModified(String objectName) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        StatObjectResponse statObjectResponse = this.minIoClient.statObject(bucketName, objectName);
        return Date.from(statObjectResponse.lastModified().toInstant());
    }

    @Override
    public FileInfo newFolder(String objectName) {
        baseOssService.printOperation(getPlatform().getKey(), "mkdir", objectName);
        if (!objectName.endsWith("/")) {
            objectName = objectName + "/";
        }
        try {
            // 上传字符串
            this.minIoClient.putObject2(
                    PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                    new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .build());
            return baseOssService.newFileInfo(objectName);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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
                objectWriteResponse = this.minIoClient.putObject2(putObjectArgs);
            }
            if (objectWriteResponse != null && objectWriteResponse.etag() != null) {
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
            PutObjectArgs putObjectArgs = getPutObjectArgs(inputStream, objectName, inputStreamLength);
            objectWriteResponse = this.minIoClient.putObject2(putObjectArgs);
            if (objectWriteResponse != null && objectWriteResponse.etag() != null) {
                // 上传成功
                baseOssService.onUploadSuccess(objectName, inputStreamLength);
            }
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
            exist = this.minIoClient.bucketExists(bucketName);
        }catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return exist;
    }

    @Override
    public boolean doesObjectExist(String objectName) {
        try {
            StatObjectResponse statObjectResponse = this.minIoClient.statObject(bucketName, objectName);
            if (statObjectResponse == null) {
                return false;
            }
            if (!statObjectResponse.deleteMarker()) {
                return true;
            }
        } catch (Exception e) {
            // ignore
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
        return new CopyOnWriteArrayList<>(getPartsList(objectName, uploadId).stream().map(Part::partNumber).toList());
    }

    private List<Part> getPartsList(String objectName, String uploadId) {
        List<Part> listParts = new ArrayList<>();
        try {
            int maxParts = 1000;
            int partNumberMarker = 0;
            boolean isTruncated;

            do {
                ListPartsResponse partResult = this.minIoClient.listMultipart(bucketName, null, objectName, maxParts, partNumberMarker, uploadId);
                List<Part> currentParts = partResult.result().partList();

                if (currentParts.isEmpty()) {
                    return Collections.emptyList();
                }

                listParts.addAll(currentParts);
                isTruncated = partResult.result().isTruncated();

                if (isTruncated) {
                    partNumberMarker = currentParts.get(currentParts.size() - 1).partNumber();
                }
            } while (isTruncated);

        } catch (Exception e) {
            log.error("Error listing parts for object: {}, uploadId: {}. Exception: {}", objectName, uploadId, e.getMessage(), e);
            return Collections.emptyList();
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
            List<Part> partList = getPartsList(objectName, uploadId);
            Part[] parts = partList.toArray(new Part[0]);
            this.minIoClient.mergeMultipartUpload(bucketName, null, objectName, uploadId, parts);
            baseOssService.onUploadSuccess(objectName, fileTotalSize);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public FileInfo getThumbnail(String objectName, File file, int width) {
        try {
            this.minIoClient.downloadObject(bucketName, objectName, file);
            byte[] bytes = ImageMagickProcessor.cropImage(file, "80", String.valueOf(width), null);
            FileUtil.writeBytes(bytes, file);
            return baseOssService.getFileInfo(objectName);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<String> copyObject(String sourceKey, String destinationKey) {
        return copyObject(bucketName, sourceKey, bucketName, destinationKey);
    }

    @Override
    public List<String> copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        baseOssService.setObjectNameLock(sourceBucketName);
        baseOssService.setObjectNameLock(destinationBucketName);
        List<String> copiedList = new ArrayList<>();
        try {
            if (sourceKey.endsWith("/")) {
                // 复制文件夹
                ListObjectsArgs listObjectsArgs = ListObjectsArgs.builder().bucket(sourceBucketName).prefix(sourceKey).recursive(true).build();
                Iterable<Result<Item>> results1 = this.minIoClient.listObjects(listObjectsArgs);
                for (Result<Item> result : results1) {
                    Item item = result.get();
                    String destKey = destinationKey + item.objectName().substring(sourceKey.length());
                    copyObjectFile(sourceBucketName, item.objectName(), destinationBucketName, destKey);
                    copiedList.add(destKey);
                }
            } else {
                // 复制文件
                copyObjectFile(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
                copiedList.add(destinationKey);
            }
            return copiedList;
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            baseOssService.removeObjectNameLock(sourceBucketName);
            baseOssService.removeObjectNameLock(destinationBucketName);
        }
        return copiedList;
    }

    private void copyObjectFile(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) throws InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException, ExecutionException, InterruptedException {
        baseOssService.printOperation(getPlatform().getKey(), "copyObject start" + "destinationKey: " + destinationKey, "sourceKey:" + sourceKey);
        this.minIoClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(destinationBucketName)
                        .object(destinationKey)
                        .source(CopySource.builder()
                                .bucket(sourceBucketName)
                                .object(sourceKey)
                                .build()).build()).get();
        baseOssService.printOperation(getPlatform().getKey(), "copyObject complete" + "destinationKey: " + destinationKey, "sourceKey:" + sourceKey);
    }

    @Override
    public URL getPresignedObjectUrl(String objectName, int expiryTime) {
        try {
            String url = this.minIoClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiryTime)
                            .build());
            return new URL(url);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
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
        baseOssService.closePrint();
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }
}
