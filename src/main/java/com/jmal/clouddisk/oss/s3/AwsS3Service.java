package com.jmal.clouddisk.oss.s3;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.Metadata;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.BaseOssService;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.PartInfo;
import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.oss.S3ObjectSummary;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.paginators.ListMultipartUploadsIterable;
import software.amazon.awssdk.services.s3.paginators.ListObjectVersionsIterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class AwsS3Service implements IOssService {

    private static final String DEFAULT_REGION = "us-east-1";
    private static final int MAX_DELETE_OBJECTS = 1000;

    private final String bucketName;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner; // 用于生成预签名URL
    private final BaseOssService baseOssService;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    private volatile boolean hasHistoryVersion = true;

    Consumer<AwsRequestOverrideConfiguration.Builder> unlimitTimeoutBuilderConsumer = builder -> builder.apiCallTimeout(Duration.ofDays(30)).build();

    public AwsS3Service(FileProperties fileProperties, OssConfigDTO ossConfigDTO) {
        this.bucketName = ossConfigDTO.getBucket();
        URI endpointUri = normalizeEndpoint(ossConfigDTO.getEndpoint());

        String region = CharSequenceUtil.isBlank(ossConfigDTO.getRegion()) ? DEFAULT_REGION : ossConfigDTO.getRegion().trim();
        boolean pathStyleAccessEnabled = BooleanUtil.isTrue(ossConfigDTO.getPathStyleAccessEnabled());
        AwsBasicCredentials credentials = AwsBasicCredentials.create(ossConfigDTO.getAccessKey(), ossConfigDTO.getSecretKey());
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccessEnabled)
                // 部分 S3-compatible 服务不支持 AWS 的流式分块签名。
                .chunkedEncodingEnabled(false)
                .build();

        // AWS SDK v2 的 S3Client 是线程安全的，推荐作为单例使用
        this.s3Client = S3Client.builder()
                .endpointOverride(endpointUri)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(region))
                // 仅在 S3 协议强制要求时发送 checksum，兼容尚未实现新版 AWS 默认 CRC32 的服务。
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(s3Configuration)
                .build();

        // S3Presigner 用于生成预签名URL，也应作为单例
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .region(Region.of(region))
                .build();

        scheduledThreadPoolExecutor = ThreadUtil.createScheduledExecutor(1);
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor, ossConfigDTO);

        Completable.fromAction(this::getMultipartUploads).subscribeOn(Schedulers.io()).doOnError(e -> log.error(e.getMessage(), e)).onErrorComplete().subscribe();
    }

    static URI normalizeEndpoint(String endpoint) {
        if (CharSequenceUtil.isBlank(endpoint)) {
            throw new IllegalArgumentException("S3 endpoint 不能为空");
        }
        String normalizedEndpoint = endpoint.trim();
        if (!normalizedEndpoint.contains("://")) {
            normalizedEndpoint = "http://" + normalizedEndpoint;
        }
        URI endpointUri = URI.create(normalizedEndpoint);
        if (endpointUri.getHost() == null) {
            throw new IllegalArgumentException("无效的 S3 endpoint: " + endpoint);
        }
        return endpointUri;
    }

    @Override
    public PlatformOSS getPlatform() {
        return PlatformOSS.MINIO;
    }

    @Override
    public Boolean getProxyEnabled() {
        return this.baseOssService.getProxyEnabled();
    }

    private void getMultipartUploads() {
        try {

            ListMultipartUploadsRequest listRequest = ListMultipartUploadsRequest.builder()
                    .bucket(bucketName)
                    .build();

            ListMultipartUploadsIterable paginator = s3Client.listMultipartUploadsPaginator(listRequest);

            // 遍历所有未完成的分片上传事件
            paginator.uploads().forEach(multipartUpload -> {
                // 删除7天前的未完成分片上传
                Instant now = Instant.now();
                Instant uploadInitiated = multipartUpload.initiated();
                Duration duration = Duration.between(uploadInitiated, now);
                if (duration.toDays() >= 7) {
                    log.info("{}, bucket: {}, 中止过时的多部分上传: objectName: {}, time: {}, uploadId: {}",
                            getPlatform().getValue(), bucketName, multipartUpload.key(), multipartUpload.initiated(), multipartUpload.uploadId());
                    abortMultipartUpload(multipartUpload.key(), multipartUpload.uploadId());
                    return;
                }
                log.info("{}, bucket: {}, 发现待处理的多部分上传: objectName: {}, time: {}, uploadId: {}",
                        getPlatform().getValue(), bucketName, multipartUpload.key(), multipartUpload.initiated(), multipartUpload.uploadId());
                baseOssService.setUpdateIdCache(multipartUpload.key(), multipartUpload.uploadId());
            });

        } catch (Exception e) {
            // 在 Native Image 或某些环境中，如果没有配置正确的 IAM 权限，
            // s3:ListMultipartUploads 可能会失败。
            log.warn("{}, bucket: {}, 无法列出存储桶的多部分上传. 。Error: {}", getPlatform().getValue(), bucketName, e.getMessage());
        }
    }

    @Override
    public AbstractOssObject getAbstractOssObject(String objectName, Long rangeStart, Long rangeEnd) {
        return getAbstractOssObject(objectName, null, rangeStart, rangeEnd);
    }

    private AbstractOssObject getAbstractOssObject(String objectName, String versionId, Long rangeStart, Long rangeEnd) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .versionId(CharSequenceUtil.isBlank(versionId) ? null : versionId)
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

            if (CharSequenceUtil.isNotBlank(versionId)) {
                getRequestBuilder.versionId(versionId);
            }

            return new AwsS3Object(headResponse, s3Client.getObject(getRequestBuilder.build()), this, bucketName, objectName);

        } catch (NoSuchKeyException e) {
            log.warn("Object not found: {}", objectName);
            return null;
        } catch (S3Exception e) {
            if (isNotFound(e)) {
                return null;
            }
            log.error("Error getting object: {}", objectName, e);
            return null;
        } catch (Exception e) {
            log.error("Error getting object: {}", objectName, e);
            return null;
        }
    }

    @Override
    public boolean deleteObject(String objectName) {
        return deleteObjectVersion(objectName, null);
    }

    @Override
    public boolean deleteObject(String objectName, String versionId) {
        return deleteObjectVersion(objectName, versionId);
    }

    private boolean deleteObjectVersion(String objectName, String versionId) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteObject", objectName);
            DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName);
            if (CharSequenceUtil.isNotBlank(versionId)) {
                builder.versionId(versionId);
                s3Client.deleteObject(builder.build());
            } else {
                deletePermanent(objectName);
            }
            return true;
        } catch (Exception e) {
            log.error("Error deleting object: {}", objectName, e);
            return false;
        }
    }

    /**
     * 永久删除对象, 删除对象的所有版本（包括删除标记）
     * @param objectName 对象名称
     */
    private void deletePermanent(String objectName) {
        List<ObjectIdentifier> toDelete = new ArrayList<>();
        ListObjectVersionsRequest listRequest = ListObjectVersionsRequest.builder()
                .bucket(bucketName)
                .prefix(objectName)
                .build();

        try {
            ListObjectVersionsIterable listObjectVersionsIterable = s3Client.listObjectVersionsPaginator(listRequest);
            listObjectVersionsIterable.forEach(response -> {
                response.versions().forEach(objectVersion -> {
                    if (objectVersion.key().equals(objectName)) {
                        toDelete.add(ObjectIdentifier.builder()
                                .key(objectName)
                                .versionId(objectVersion.versionId())
                                .build());
                    }
                });
                response.deleteMarkers().forEach(marker -> {
                    if (marker.key().equals(objectName)) {
                        toDelete.add(ObjectIdentifier.builder()
                                .key(objectName)
                                .versionId(marker.versionId())
                                .build());
                    }
                });
            });
        } catch (S3Exception e) {
            // Versioning API 并非所有 S3-compatible 服务都实现，退化为标准删除当前对象。
            log.debug("无法列举对象版本，改为删除当前对象: {}, error: {}", objectName, errorCode(e));
        }

        if (!toDelete.isEmpty()) {
            deleteObjectsInBatches(bucketName, toDelete);
        } else {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectName).build());
        }
    }

    @Override
    public void restoreVersion(String objectName, String versionId) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "restoreVersion", objectName);
            // 复制指定版本到当前版本
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(objectName)
                    .sourceVersionId(versionId)
                    .destinationBucket(bucketName)
                    .destinationKey(objectName)
                    .build();
            s3Client.copyObject(copyRequest);
        } catch (Exception e) {
            log.error("Error restoring version for object: {}", objectName, e);
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

            // S3 DeleteObjects 每次最多允许 1000 个 key。
            return deleteObjectsInBatches(bucketName, keysToDelete);
        } catch (Exception e) {
            log.error("Error deleting directory: {}", objectName, e);
            return false;
        }
    }

    private boolean deleteObjectsInBatches(String targetBucket, List<ObjectIdentifier> objectIdentifiers) {
        boolean success = true;
        for (int start = 0; start < objectIdentifiers.size(); start += MAX_DELETE_OBJECTS) {
            List<ObjectIdentifier> batch = objectIdentifiers.subList(start,
                    Math.min(start + MAX_DELETE_OBJECTS, objectIdentifiers.size()));
            try {
                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(targetBucket)
                        .delete(Delete.builder().objects(batch).quiet(true).build())
                        .build();
                DeleteObjectsResponse response = s3Client.deleteObjects(deleteRequest);
                if (response.hasErrors()) {
                    for (S3Error error : response.errors()) {
                        log.error("删除 S3 对象失败, key: {}, code: {}, message: {}",
                                error.key(), error.code(), error.message());
                        success = false;
                    }
                }
            } catch (S3Exception e) {
                // 少数 S3-compatible 服务没有实现批量删除，逐个删除仍属于基础 S3 能力。
                log.debug("批量删除不可用，改为逐个删除 {} 个对象, error: {}", batch.size(), errorCode(e));
                for (ObjectIdentifier objectIdentifier : batch) {
                    try {
                        DeleteObjectRequest.Builder request = DeleteObjectRequest.builder()
                                .bucket(targetBucket)
                                .key(objectIdentifier.key());
                        if (CharSequenceUtil.isNotBlank(objectIdentifier.versionId())) {
                            request.versionId(objectIdentifier.versionId());
                        }
                        s3Client.deleteObject(request.build());
                    } catch (S3Exception deleteException) {
                        log.error("删除 S3 对象失败: {}", objectIdentifier.key(), deleteException);
                        success = false;
                    }
                }
            }
        }
        return success;
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
            for (ListObjectsV2Response response : s3Client.listObjectsV2Paginator(listRequest)) {
                for (S3Object s3Object : response.contents()) {
                    S3ObjectSummary summary = new S3ObjectSummary(s3Object.size(), s3Object.key(), s3Object.eTag(), Date.from(s3Object.lastModified()), bucketName);
                    baseOssService.addFileInfoList(objectName, fileInfoList, summary);
                }

                for (CommonPrefix commonPrefix : response.commonPrefixes()) {
                    String key = commonPrefix.prefix();
                    if (fileInfoList.stream().noneMatch(fileInfo -> key.equals(fileInfo.getKey()))) {
                        fileInfoList.add(baseOssService.newFileInfo(key));
                    }
                }
            }
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
    public boolean uploadFile(InputStream inputStream, String objectName, long inputStreamLength) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "uploadFile inputStream", objectName);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(baseOssService.getContentType(objectName))
                    .build();
            s3Client.putObject(request, createSmartRequestBody(inputStream, inputStreamLength));
            baseOssService.onUploadSuccess(objectName, inputStreamLength);
            return true;
        } catch (Exception e) {
            log.error("Error uploading from stream: {}", objectName, e);
        }
        return false;
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
            HeadObjectResponse headObjectResponse = s3Client.headObject(request);
            return BooleanUtil.isFalse(headObjectResponse.deleteMarker());
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (isNotFound(e)) {
                return false;
            }
            log.error("Error checking object existence: {}", objectName, e);
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
            log.info("Completing multipart upload for object: {}, uploadId: {}", objectName, uploadId);
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

    /**
     * 完成分片上传 - 使用前端传递的 parts 信息
     */
    public void completeMultipartUploadWithParts(String objectName, String uploadId, List<PartInfo> parts, Long fileTotalSize) {
        try {

            List<CompletedPart> completedParts = parts.stream()
                    .map(part -> CompletedPart.builder()
                            .partNumber(part.getPartNumber())
                            .eTag(part.getEtag())
                            .build())
                    .sorted(Comparator.comparing(CompletedPart::partNumber))
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

            CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(request);
            log.debug("Complete success - location: {}, etag: {}", response.location(), response.eTag());

            baseOssService.onUploadSuccess(objectName, fileTotalSize);
        } catch (Exception e) {
            log.error("Error completing multipart upload for: {}", objectName, e);
            throw new RuntimeException("Failed to complete multipart upload: " + e.getMessage(), e);
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
    public String getPresignedObjectUrl(String objectName, int expiryTime, boolean isDownload) {
        try {
            GetObjectRequest.Builder getRequestBuilder = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName);
            if (isDownload) {
                String downloadFileName = Path.of(objectName).getFileName().toString();
                String contentDisposition = "attachment; filename=\"" + downloadFileName + "\"";
                String contentType = baseOssService.getContentType(objectName);
                getRequestBuilder.responseContentType(contentType);
                getRequestBuilder.responseContentDisposition(contentDisposition);
            }
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiryTime))
                    .getObjectRequest(getRequestBuilder.build())
                    .build();
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned URL for: {}", objectName, e);
            return null;
        }
    }

    @Override
    public String getPresignedPutUrl(String objectName, String contentType, int expiryTime) {
        try {
            PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName);
            if (contentType != null && !contentType.isEmpty()) {
                putBuilder.contentType(contentType);
            }
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiryTime))
                    .putObjectRequest(putBuilder.build())
                    .build();
            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned PUT URL for: {}", objectName, e);
            return null;
        }
    }

    /**
     * 生成分片上传的预签名URL
     */
    public String getPresignedUploadPartUrl(String objectName, String uploadId, int partNumber, int expiryTime) {
        try {
            // String decodedObjectName = URLDecoder.decode(objectName, StandardCharsets.UTF_8);
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiryTime))
                    .uploadPartRequest(uploadPartRequest)
                    .build();

            PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned upload part URL for: {}, partNumber: {}", objectName, partNumber, e);
            return null;
        }
    }

    /**
     * 批量生成分片上传的预签名URL
     */
    @Override
    public Map<Integer, String> getPresignedUploadPartUrls(String objectName, String uploadId, int totalParts, int expiryTime) {
        log.info("Generating {} presigned upload part URLs for object: {}, uploadId: {}", totalParts, objectName, uploadId);
        Map<Integer, String> urlMap = new HashMap<>(totalParts);

        for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
            String url = getPresignedUploadPartUrl(objectName, uploadId, partNumber, expiryTime);
            if (url != null) {
                urlMap.put(partNumber, url);
            }
        }

        return urlMap;
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
    public boolean write(InputStream inputStream, String ossPath, String objectName, long size) {
        return uploadFile(inputStream, objectName, size);
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
        return getAbstractOssObject(objectName, null, null, null);
    }

    @Override
    public AbstractOssObject getAbstractOssObject(String objectName, String versionId) {
        return getAbstractOssObject(objectName, versionId, null, null);
    }

    @Override
    public Page<GridFSBO> listObjectVersions(String objectName, Integer pageSize, Integer pageIndex) {
        if (!hasHistoryVersion) {
            return Page.empty();
        }

        List<GridFSBO> allVersions = new ArrayList<>();
        String keyMarker = null;
        String versionIdMarker = null;

        int skipCount = (pageIndex - 1) * pageSize;
        int totalCount = 0;
        boolean hasMore = true;

        try {
            while (hasMore) {
                ListObjectVersionsRequest request = ListObjectVersionsRequest.builder()
                        .bucket(bucketName)
                        .prefix(objectName)
                        .keyMarker(keyMarker)
                        .versionIdMarker(versionIdMarker)
                        .maxKeys(1000)
                        .build();

                ListObjectVersionsResponse response = s3Client.listObjectVersions(request);

                // 只筛选精确匹配 objectName 的版本
                for (ObjectVersion version : response.versions()) {
                    if (version.key().equals(objectName)) {
                        totalCount++;
                        // 跳过前面的页，收集当前页的数据
                        if (totalCount > skipCount && allVersions.size() < pageSize) {
                            allVersions.add(getGridFSBO(version));
                        }
                    }
                }

                // 检查是否还有更多数据
                if (response.isTruncated()) {
                    keyMarker = response.nextKeyMarker();
                    versionIdMarker = response.nextVersionIdMarker();
                } else {
                    hasMore = false;
                }

                // 如果已经收集够当前页的数据，且不需要统计总数，可以提前退出
                if (allVersions.size() >= pageSize && !response.isTruncated()) {
                    break;
                }
            }
        } catch (S3Exception e) {
            if (isUnsupported(e)) {
                log.warn("当前 S3-compatible 服务不支持对象版本列表, bucket: {}", bucketName);
                this.hasHistoryVersion = false;
                return Page.empty();
            }
            log.warn("列举对象版本失败: {}, code: {}", objectName, errorCode(e));
        } catch (Exception e) {
            log.error("Unexpected error listing object versions for: {}", objectName, e);
        }
        return new PageImpl<>(allVersions, PageRequest.of(pageIndex - 1, pageSize), totalCount);
    }

    private static GridFSBO getGridFSBO(ObjectVersion version) {
        String objectName = version.key();
        String filename = Path.of(objectName).getFileName().toString();
        GridFSBO gridFSBO = new GridFSBO();
        gridFSBO.setId(version.versionId());
        gridFSBO.setUploadDate(LocalDateTimeUtil.of(version.lastModified()));
        Metadata metadata = new Metadata();
        metadata.setSize(version.size());
        metadata.setFilename(filename);
        metadata.setTime(LocalDateTimeUtil.format(gridFSBO.getUploadDate(), "yyyy-MM-dd HH:mm:ss"));
        gridFSBO.setMetadata(metadata);
        return gridFSBO;
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
        } catch (S3Exception e) {
            if (isNotFound(e)) {
                return false;
            }
            log.error("Error checking bucket existence: {}", bucketName, e);
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

    private static boolean isNotFound(S3Exception exception) {
        return exception.statusCode() == 404
                || "NoSuchKey".equalsIgnoreCase(errorCode(exception))
                || "NoSuchBucket".equalsIgnoreCase(errorCode(exception));
    }

    private static boolean isUnsupported(S3Exception exception) {
        String code = errorCode(exception);
        return exception.statusCode() == 501
                || "NotImplemented".equalsIgnoreCase(code)
                || "UnsupportedOperation".equalsIgnoreCase(code);
    }

    private static String errorCode(S3Exception exception) {
        return exception.awsErrorDetails() == null ? null : exception.awsErrorDetails().errorCode();
    }
}
