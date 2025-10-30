package com.jmal.clouddisk.oss.s3; // 建议为 AWS S3 创建一个新的包

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class AwsS3Service implements IOssService {

    private final String bucketName;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner; // 用于生成预签名URL
    private final BaseOssService baseOssService;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    Consumer<AwsRequestOverrideConfiguration.Builder> unlimitTimeoutBuilderConsumer = builder -> builder.apiCallTimeout(Duration.ofDays(30)).build();

    public AwsS3Service(FileProperties fileProperties, OssConfigDTO ossConfigDTO) {
        this.bucketName = ossConfigDTO.getBucket();
        URI endpointUri = URI.create(ossConfigDTO.getEndpoint());

        // AWS SDK v2 的 S3Client 是线程安全的，推荐作为单例使用
        this.s3Client = S3Client.builder()
                .endpointOverride(endpointUri)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ossConfigDTO.getAccessKey(), ossConfigDTO.getSecretKey())))
                .region(Region.of(ossConfigDTO.getRegion() == null ? "cn1" : ossConfigDTO.getRegion()))
                // 连接 MinIO 时必须开启路径风格访问
                .forcePathStyle(true)
                .serviceConfiguration(S3Configuration.builder()
                        // 启用无签名负载。这会设置 x-amz-content-sha256: UNSIGNED-PAYLOAD
                        .chunkedEncodingEnabled(false) // 对于MinIO，禁用分块编码通常更稳定
                        .build())
                .build();

        // S3Presigner 用于生成预签名URL，也应作为单例
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ossConfigDTO.getAccessKey(), ossConfigDTO.getSecretKey())))
                .region(Region.of(ossConfigDTO.getRegion() == null ? "cn1" : ossConfigDTO.getRegion()))
                .build();

        scheduledThreadPoolExecutor = ThreadUtil.createScheduledExecutor(1);
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor, ossConfigDTO);

        // Completable.fromAction(this::getMultipartUploads).subscribeOn(Schedulers.io()).doOnError(e -> log.error(e.getMessage(), e)).onErrorComplete().subscribe();
    }

    @Override
    public PlatformOSS getPlatform() {
        return PlatformOSS.MINIO;
    }

    // private void getMultipartUploads() {
    //     try {
    //
    //         ListMultipartUploadsRequest listRequest = ListMultipartUploadsRequest.builder()
    //                 .bucket(bucketName)
    //                 .build();
    //
    //         ListMultipartUploadsIterable paginator = s3Client.listMultipartUploadsPaginator(listRequest);
    //
    //         // 遍历所有未完成的分片上传事件
    //         paginator.uploads().forEach(multipartUpload -> {
    //             log.info("{}, Found pending multipart upload: objectName: {}, uploadId: {}",
    //                     getPlatform().getValue(), multipartUpload.key(), multipartUpload.uploadId());
    //
    //             baseOssService.setUpdateIdCache(multipartUpload.key(), multipartUpload.uploadId());
    //         });
    //
    //     } catch (Exception e) {
    //         // 在 Native Image 或某些环境中，如果没有配置正确的 IAM 权限，
    //         // s3:ListMultipartUploads 可能会失败。
    //         log.error("Failed to list multipart uploads for bucket '{}'. " +
    //                         "Please ensure the credentials have 's3:ListMultipartUploads' permission. Error: {}",
    //                 bucketName, e.getMessage(), e);
    //     }
    // }

    @Override
    public AbstractOssObject getAbstractOssObject(String objectName, Long rangeStart, Long rangeEnd) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);

            GetObjectRequest.Builder getRequestBuilder = GetObjectRequest.builder()
                    .overrideConfiguration(unlimitTimeoutBuilderConsumer)
                    .bucket(bucketName)
                    .key(objectName);

            if (rangeStart != null) {
                String range = "bytes=" + rangeStart + "-" + (rangeEnd != null ? rangeEnd : "");
                getRequestBuilder.range(range);
            }

            return new AwsS3Object(headResponse, s3Client.getObject(getRequestBuilder.build()), this, bucketName, objectName);

        } catch (NoSuchKeyException e) {
            log.warn("Object not found: {}", objectName);
            return null;
        } catch (Exception e) {
            log.error("Error getting object: {}", objectName, e);
            return null;
        }
    }

    @Override
    public boolean deleteObject(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteObject", objectName);
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            s3Client.deleteObject(deleteRequest);
            return true;
        } catch (Exception e) {
            log.error("Error deleting object: {}", objectName, e);
            return false;
        }
    }

    @Override
    public boolean deleteDir(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteDir", objectName);
            // 1. 列出目录下所有对象
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(objectName)
                    .build();
            List<ObjectIdentifier> keysToDelete = s3Client.listObjectsV2Paginator(listRequest)
                    .contents().stream()
                    .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
                    .collect(Collectors.toList());

            // 如果文件夹为空，直接返回成功
            if (keysToDelete.isEmpty()) {
                return true;
            }

            // 2. 批量删除
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(keysToDelete).build())
                    .build();
            DeleteObjectsResponse response = s3Client.deleteObjects(deleteRequest);

            // 检查是否有删除失败的对象
            if (response.hasErrors()) {
                response.errors().forEach(error ->
                        log.error("Error deleting object {}: {}", error.key(), error.message()));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Error deleting directory: {}", objectName, e);
            return false;
        }
    }

    @Override
    public List<FileInfo> getFileInfoList(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(objectName)
                    .delimiter("/")
                    .build();
            ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

            // 处理文件
            for (S3Object s3Object : response.contents()) {
                S3ObjectSummary summary = new S3ObjectSummary(s3Object.size(), s3Object.key(), s3Object.eTag(), Date.from(s3Object.lastModified()), bucketName);
                baseOssService.addFileInfoList(objectName, fileInfoList, summary);
            }

            // 处理子目录 (CommonPrefixes)
            for (CommonPrefix commonPrefix : response.commonPrefixes()) {
                String key = commonPrefix.prefix();
                if (fileInfoList.stream().noneMatch(fileInfo -> key.equals(fileInfo.getKey()))) {
                    fileInfoList.add(baseOssService.newFileInfo(key));
                }
            }
            // AWS SDK 直接返回 LastModified，通常不需要二次查询
        } catch (Exception e) {
            log.error("Error listing files for prefix: {}", objectName, e);
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
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(new byte[0]));
            return baseOssService.newFileInfo(objectName);
        } catch (Exception e) {
            log.error("Error creating folder: {}", objectName, e);
        }
        return null;
    }

    @Override
    public void uploadFile(Path tempFileAbsolutePath, String objectName) {
        try {
            if (!PathUtil.exists(tempFileAbsolutePath, false)) return;
            baseOssService.printOperation(getPlatform().getKey(), "upload", objectName);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(baseOssService.getContentType(objectName))
                    .build();
            s3Client.putObject(request, RequestBody.fromFile(tempFileAbsolutePath));
            baseOssService.onUploadSuccess(objectName, tempFileAbsolutePath);
        } catch (Exception e) {
            log.error("Error uploading file: {}", objectName, e);
        }
    }

    @Override
    public void uploadFile(InputStream inputStream, String objectName, long inputStreamLength) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "uploadFile inputStream", objectName);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(baseOssService.getContentType(objectName))
                    .build();
            s3Client.putObject(request, createSmartRequestBody(inputStream, inputStreamLength));
            baseOssService.onUploadSuccess(objectName, inputStreamLength);
        } catch (Exception e) {
            log.error("Error uploading from stream: {}", objectName, e);
        }
    }

    /**
     * 一个智能的、适应不同 InputStream 来源的 RequestBody。
     * 这是解决 mark/reset 问题的核心。
     *
     * @param inputStream 原始输入流
     * @param length      流的长度
     * @return 一个适合上传的 RequestBody
     * @throws IOException 如果创建临时文件或读取流失败
     */
    private RequestBody createSmartRequestBody(InputStream inputStream, long length) throws IOException {
        // 检查流是否已经支持 mark/reset。如果是，直接使用，零开销！
        // 比如 BufferedInputStream 就会返回 true。
        if (inputStream.markSupported()) {
            log.debug("InputStream supports mark/reset. Using directly.");
            return RequestBody.fromInputStream(inputStream, length);
        }

        // 是缓冲到内存还是临时文件？
        final long MEMORY_BUFFER_THRESHOLD = 20 * 1024 * 1024; // 20 MB

        if (length < MEMORY_BUFFER_THRESHOLD) {
            log.debug("InputStream does not support mark/reset. Buffering to memory (size: {} bytes).", length);
            // 缓冲到内存
            byte[] contentBytes = StreamUtils.copyToByteArray(inputStream);
            return RequestBody.fromBytes(contentBytes);
        } else {
            log.debug("InputStream does not support mark/reset. Buffering to a temporary file (size: {} bytes).", length);
            // 缓冲到临时文件
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("s3-upload-", ".tmp");
                // 将流的内容复制到临时文件
                Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return RequestBody.fromFile(tempFile);
            } catch (IOException e) {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException cleanupException) {
                        e.addSuppressed(cleanupException);

                    }
                }
                throw e;
            }
        }
    }

    @Override
    public boolean doesObjectExist(String objectName) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking object existence: {}", objectName, e);
            return false;
        }
    }

    @Override
    public String initiateMultipartUpload(String objectName) {
        try {
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(baseOssService.getContentType(objectName))
                    .build();
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
            return response.uploadId();
        } catch (Exception e) {
            log.error("Error initiating multipart upload for: {}", objectName, e);
            return null;
        }
    }

    @Override
    public boolean uploadPart(InputStream inputStream, String objectName, int partSize, int partNumber, String uploadId) {
        try {
            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            s3Client.uploadPart(request, createSmartRequestBody(inputStream, partSize));
            return true;
        } catch (Exception e) {
            log.error("Error uploading part #{} for object: {}", partNumber, objectName, e);
            return false;
        }
    }

    private List<Part> getPartsList(String objectName, String uploadId) {
        try {
            ListPartsRequest request = ListPartsRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .build();
            return s3Client.listPartsPaginator(request).parts().stream().collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error listing parts for uploadId: {}", uploadId, e);
            return List.of();
        }
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        try {
            AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .build();
            s3Client.abortMultipartUpload(request);
        } catch (Exception e) {
            log.error("Error aborting multipart upload for: {}", objectName, e);
        }
    }

    @Override
    public void completeMultipartUpload(String objectName, String uploadId, Long fileTotalSize) {
        try {
            List<Part> parts = getPartsList(objectName, uploadId);
            List<CompletedPart> completedParts = parts.stream()
                    .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.eTag()).build())
                    .collect(Collectors.toList());

            CompletedMultipartUpload completedInfo = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .multipartUpload(completedInfo)
                    .build();
            s3Client.completeMultipartUpload(request);
            baseOssService.onUploadSuccess(objectName, fileTotalSize);
        } catch (Exception e) {
            log.error("Error completing multipart upload for: {}", objectName, e);
        }
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
                ListObjectsV2Request listRequest = ListObjectsV2Request.builder().bucket(sourceBucketName).prefix(sourceKey).build();
                s3Client.listObjectsV2Paginator(listRequest).contents().forEach(s3Object -> {
                    String destKey = destinationKey + s3Object.key().substring(sourceKey.length());
                    copyObjectFile(sourceBucketName, s3Object.key(), destinationBucketName, destKey);
                    copiedList.add(destKey);
                });
            } else {
                // 复制文件
                copyObjectFile(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
                copiedList.add(destinationKey);
            }
        } catch (Exception e) {
            log.error("Error copying object from {} to {}", sourceKey, destinationKey, e);
        } finally {
            baseOssService.removeObjectNameLock(sourceBucketName);
            baseOssService.removeObjectNameLock(destinationBucketName);
        }
        return copiedList;
    }

    private void copyObjectFile(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        baseOssService.printOperation(getPlatform().getKey(), "copyObject", "from " + sourceKey + " to " + destinationKey);
        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(sourceBucketName)
                .sourceKey(sourceKey)
                .destinationBucket(destinationBucketName)
                .destinationKey(destinationKey)
                .build();
        s3Client.copyObject(request);
    }

    @Override
    public String getPresignedObjectUrl(String objectName, int expiryTime) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiryTime))
                    .getObjectRequest(getRequest)
                    .build();
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned URL for: {}", objectName, e);
            return null;
        }
    }

    // 以下是继承自 IOssService 但未在 MinIOService 中详细实现的方法，
    // 将它们链接到 baseOssService 或提供简单实现。

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
    public List<FileInfo> getFileInfoListCache(String objectName) {
        return baseOssService.getFileInfoListCache(objectName);
    }

    @Override
    public List<FileInfo> getAllObjectsWithPrefix(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder().bucket(bucketName).prefix(objectName).build();
            s3Client.listObjectsV2Paginator(listRequest).contents().forEach(s3Object -> {
                S3ObjectSummary summary = new S3ObjectSummary(s3Object.size(), s3Object.key(), s3Object.eTag(), Date.from(s3Object.lastModified()), bucketName);
                fileInfoList.add(baseOssService.getFileInfo(summary));
            });
        } catch (Exception e) {
            log.error("Error getting all objects with prefix: {}", objectName, e);
        }
        return fileInfoList;
    }

    @Override
    public boolean doesBucketExist() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking bucket existence: {}", bucketName, e);
            return false;
        }
    }

    @Override
    public String getUploadId(String objectName) {
        return baseOssService.getUploadId(objectName);
    }

    @Override
    public CopyOnWriteArrayList<Integer> getListParts(String objectName, String uploadId) {
        return new CopyOnWriteArrayList<>(getPartsList(objectName, uploadId).stream().map(Part::partNumber).toList());
    }

    @Override
    public InputStream getThumbnail(String objectName, int width) {
        try (InputStream is = s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectName).build())) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageMagickProcessor.cropImage(is, "80", String.valueOf(width), null, byteArrayOutputStream);
            return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error downloading thumbnail: {}", objectName, e);
            return null;
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
        baseOssService.closePrint();
        if (scheduledThreadPoolExecutor != null) scheduledThreadPoolExecutor.shutdown();
        if (s3Client != null) s3Client.close();
        if (s3Presigner != null) s3Presigner.close();
    }
}
