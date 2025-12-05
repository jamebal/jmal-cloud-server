package com.jmal.clouddisk.oss.aliyun;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListMultipartUploadsRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.MultipartUpload;
import com.aliyun.oss.model.MultipartUploadListing;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.UploadPartCopyRequest;
import com.aliyun.oss.model.UploadPartCopyResult;
import com.aliyun.oss.model.UploadPartRequest;
import com.jmal.clouddisk.config.FileProperties;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
public class AliyunOssService implements IOssService {

    private final String bucketName;

    private final OSS ossClient;

    private final BaseOssService baseOssService;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public AliyunOssService(FileProperties fileProperties, OssConfigDTO ossConfigDTO) {
        String endpoint = ossConfigDTO.getEndpoint();
        String accessKeyId = ossConfigDTO.getAccessKey();
        String accessKeySecret = ossConfigDTO.getSecretKey();
        this.bucketName = ossConfigDTO.getBucket();
        // 创建OSSClient实例。
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        scheduledThreadPoolExecutor = ThreadUtil.createScheduledExecutor(1);
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor, ossConfigDTO);
        Completable.fromAction(this::getMultipartUploads).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
    }

    @Override
    public PlatformOSS getPlatform() {
        return PlatformOSS.ALIYUN;
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
        OSSObject ossObject = null;
        try {
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectName);
            if (rangeStart != null && rangeEnd != null) {
                getObjectRequest.setRange(rangeStart, rangeEnd);
            }
            ossObject = this.ossClient.getObject(getObjectRequest);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        if (ossObject == null) {
            return null;
        }
        return new AliyunOssObject(ossObject, this);
    }


    @Override
    public boolean deleteObject(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteObject", objectName);
            ossClient.deleteObject(bucketName, objectName);
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
            // 删除目录及目录下的所有文件。
            String nextMarker = null;
            ObjectListing objectListing;
            do {
                ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName)
                        .withPrefix(objectName)
                        .withMarker(nextMarker);

                objectListing = ossClient.listObjects(listObjectsRequest);
                if (!objectListing.getObjectSummaries().isEmpty()) {
                    List<String> keys = new ArrayList<>();
                    for (OSSObjectSummary s : objectListing.getObjectSummaries()) {
                        keys.add(s.getKey());
                    }
                    DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(keys).withEncodingType("url");
                    ossClient.deleteObjects(deleteObjectsRequest);
                }
                nextMarker = objectListing.getNextMarker();
            } while (objectListing.isTruncated());
            return true;
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return false;
    }

    @Override
    public List<FileInfo> getFileInfoList(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            // 构造ListObjectsRequest请求。
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName);

            // 设置正斜线（/）为文件夹的分隔符。
            listObjectsRequest.setDelimiter("/");

            // 列出fun目录下的所有文件和文件夹。
            listObjectsRequest.setPrefix(objectName);
            baseOssService.printOperation(getPlatform().getKey(), "getFileInfoList", objectName);
            ObjectListing listing = ossClient.listObjects(listObjectsRequest);

            // 对象列表
            for (OSSObjectSummary objectSummary : listing.getObjectSummaries()) {
                S3ObjectSummary s3ObjectSummary = new S3ObjectSummary(objectSummary.getSize(), objectSummary.getKey(), objectSummary.getETag(), objectSummary.getLastModified(), objectSummary.getBucketName());
                baseOssService.addFileInfoList(objectName, fileInfoList, s3ObjectSummary);
            }
            // 子目录
            for (String commonPrefix : listing.getCommonPrefixes()) {
                fileInfoList.add(baseOssService.newFileInfo(commonPrefix));
            }
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return fileInfoList;
    }

    @Override
    public List<FileInfo> getAllObjectsWithPrefix(String objectName) {
        // 列举所有包含指定前缀的所有文件
        String nextMarker = null;
        ObjectListing objectListing;
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            do {
                ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName).withPrefix(objectName).withMarker(nextMarker);
                objectListing = ossClient.listObjects(listObjectsRequest);
                if (!objectListing.getObjectSummaries().isEmpty()) {
                    objectListing.getObjectSummaries().forEach(ossObjectSummary -> fileInfoList.add(new FileInfo(ossObjectSummary.getKey(), ossObjectSummary.getETag(), ossObjectSummary.getSize(), ossObjectSummary.getLastModified())));
                }
                nextMarker = objectListing.getNextMarker();
            } while (objectListing.isTruncated());
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return fileInfoList;
    }

    @Override
    public List<FileInfo> getFileInfoListCache(String objectName) {
        return baseOssService.getFileInfoListCache(objectName);
    }

    @Override
    public boolean doesBucketExist() {
        boolean exist;
        exist = this.ossClient.doesBucketExist(bucketName);
        if (exist) {
            this.ossClient.getBucketStat(bucketName);
        }
        return exist;
    }

    public String getUploadId(String objectName) {
        return baseOssService.getUploadId(objectName);
    }

    @Override
    public String initiateMultipartUpload(String objectName) {
        // 创建InitiateMultipartUploadRequest对象。
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectName);
        // 初始化分片。
        InitiateMultipartUploadResult uploadResult = ossClient.initiateMultipartUpload(request);
        // 返回uploadId，它是分片上传事件的唯一标识。您可以根据该uploadId发起相关的操作，例如取消分片上传、查询分片上传等。
        return uploadResult.getUploadId();
    }

    private void getMultipartUploads() {
        try {
            // 列举分片上传事件。
            MultipartUploadListing multipartUploadListing;
            ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(bucketName);
            // 设置每页列举的分片上传事件个数。
            listMultipartUploadsRequest.setMaxUploads(50);
            do {
                multipartUploadListing = ossClient.listMultipartUploads(listMultipartUploadsRequest);
                for (MultipartUpload multipartUpload : multipartUploadListing.getMultipartUploads()) {
                    log.info("{}, 碎片文件: objectName: {}, uploadId: {}", getPlatform().getValue(), multipartUpload.getKey(), multipartUpload.getUploadId());
                    baseOssService.setUpdateIdCache(multipartUpload.getKey(), multipartUpload.getUploadId());
                }
                listMultipartUploadsRequest.setKeyMarker(multipartUploadListing.getNextKeyMarker());
                listMultipartUploadsRequest.setUploadIdMarker(multipartUploadListing.getNextUploadIdMarker());
            } while (multipartUploadListing.isTruncated());
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
    }

    @Override
    public boolean doesObjectExist(String objectName) {
        return this.ossClient.doesObjectExist(bucketName, objectName);
    }

    @Override
    public CopyOnWriteArrayList<Integer> getListParts(String objectName, String uploadId) {
        return new CopyOnWriteArrayList<>(getPartETagList(objectName, uploadId).stream().map(PartETag::getPartNumber).toList());
    }

    private List<PartETag> getPartETagList(String objectName, String uploadId) {
        List<PartETag> listParts = new ArrayList<>();
        try {
            // 列举所有已上传的分片。
            PartListing partListing;
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            do {
                partListing = ossClient.listParts(listPartsRequest);
                for (PartSummary part : partListing.getParts()) {
                    listParts.add(new PartETag(part.getPartNumber(), part.getETag()));
                }
                // 指定List的起始位置，只有分片号大于此参数值的分片会被列出。
                listPartsRequest.setPartNumberMarker(partListing.getNextPartNumberMarker());
            } while (partListing.isTruncated());
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return listParts;
    }

    @Override
    public boolean uploadPart(InputStream inputStream, String objectName, int partSize, int partNumber, String uploadId) {
        UploadPartRequest uploadPartRequest = new UploadPartRequest();
        uploadPartRequest.setBucketName(bucketName);
        uploadPartRequest.setKey(objectName);
        uploadPartRequest.setUploadId(uploadId);
        // 设置上传的分片流。
        uploadPartRequest.setInputStream(inputStream);
        // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100 KB。
        uploadPartRequest.setPartSize(partSize);
        // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出此范围，OSS将返回InvalidArgument错误码。
        uploadPartRequest.setPartNumber(partNumber);
        // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
        try {
            this.ossClient.uploadPart(uploadPartRequest);
            return true;
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return false;
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        try {
            // 取消分片上传。
            AbortMultipartUploadRequest abortMultipartUploadRequest = new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
            ossClient.abortMultipartUpload(abortMultipartUploadRequest);
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
    }

    @Override
    public void completeMultipartUpload(String objectName, String uploadId, Long totalSize) {
        baseOssService.printOperation(getPlatform().getKey(), "completeMultipartUpload", objectName);
        List<PartETag> partETags = getPartETagList(objectName, uploadId);
        // 创建CompleteMultipartUploadRequest对象。
        // 在执行完成分片上传操作时，需要提供所有有效的partETags。OSS收到提交的partETags后，会逐一验证每个分片的有效性。当所有的数据分片验证通过后，OSS将把这些分片组合成一个完整的文件。
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partETags);
        // 完成分片上传。
        ossClient.completeMultipartUpload(completeMultipartUploadRequest);
        baseOssService.onUploadSuccess(objectName, totalSize);
    }

    @Override
    public void completeMultipartUploadWithParts(String objectName, String uploadId, List<PartInfo> partInfoList, Long fileTotalSize) {
        baseOssService.printOperation(getPlatform().getKey(), "completeMultipartUploadWithParts", objectName);
        List<PartETag> partETags = new ArrayList<>();
        for (PartInfo partInfo : partInfoList) {
            partETags.add(new PartETag(partInfo.getPartNumber(), partInfo.getEtag()));
        }
        // 创建CompleteMultipartUploadRequest对象。
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partETags);
        // 完成分片上传。
        ossClient.completeMultipartUpload(completeMultipartUploadRequest);
        baseOssService.onUploadSuccess(objectName, fileTotalSize);
    }

    @Override
    public InputStream getThumbnail(String objectName, int width) {
        try {
            // 将图片缩放为固定宽高100 px。
            String style = "image/resize,m_mfit,w_" + width;
            GetObjectRequest request = new GetObjectRequest(bucketName, objectName);
            request.setProcess(style);
            // 将处理后的图片命名为example-resize.jpg并保存到本地。
            // 如果未指定本地路径只填写了本地文件名称（例如example-resize.jpg），则文件默认保存到示例程序所属项目对应本地路径中。
            return ossClient.getObject(request).getObjectContent();
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
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
            String content = "";
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, new ByteArrayInputStream(content.getBytes()));
            // 上传字符串
            ossClient.putObject(putObjectRequest);
            return baseOssService.newFileInfo(objectName);
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return null;
    }

    @Override
    public void uploadFile(Path tempFileAbsolutePath, String objectName) {
        executeUpload(tempFileAbsolutePath, objectName);
    }

    private void executeUpload(Path tempFileAbsolutePath, String objectName) {
        try {
            if (!PathUtil.exists(tempFileAbsolutePath, false)) {
                return;
            }
            baseOssService.printOperation(getPlatform().getKey(), "upload", objectName);
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, tempFileAbsolutePath.toFile());
            // 设置该属性可以返回response。如果不设置，则返回的response为空。
            putObjectRequest.setProcess("true");
            // 创建PutObject请求。
            PutObjectResult putObjectResult = this.ossClient.putObject(putObjectRequest);
            if (putObjectResult.getResponse().getStatusCode() == 200) {
                // 上传成功
                baseOssService.onUploadSuccess(objectName, tempFileAbsolutePath);
            }
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
    }

    @Override
    public void uploadFile(InputStream inputStream, String objectName, long inputStreamLength) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "uploadFile inputStream", objectName);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(inputStreamLength);
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream, objectMetadata);
            // 创建PutObject请求。
            ossClient.putObject(putObjectRequest);
            baseOssService.onUploadSuccess(objectName, inputStreamLength);
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
    }

    public List<String> copyObject(String sourceKey, String destinationKey) {
        return copyObject(bucketName, sourceKey, bucketName, destinationKey);
    }

    public List<String> copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        baseOssService.setObjectNameLock(sourceBucketName);
        baseOssService.setObjectNameLock(destinationBucketName);
        List<String> copiedList = new ArrayList<>();
        try {
            if (sourceKey.endsWith("/")) {
                // 复制文件夹
                // 列举所有包含指定前缀的文件并copy
                String nextMarker = null;
                ObjectListing objectListing;
                do {
                    ListObjectsRequest listObjectsRequest = new ListObjectsRequest(sourceBucketName).withPrefix(sourceKey).withMarker(nextMarker);
                    objectListing = ossClient.listObjects(listObjectsRequest);
                    if (!objectListing.getObjectSummaries().isEmpty()) {
                        objectListing.getObjectSummaries().parallelStream().forEach(ossObjectSummary -> {
                            String destKey = destinationKey + ossObjectSummary.getKey().substring(sourceKey.length());
                            copyObjectFile(ossObjectSummary.getBucketName(), ossObjectSummary.getKey(), destinationBucketName, destKey);
                            copiedList.add(destKey);
                        });
                    }
                    nextMarker = objectListing.getNextMarker();
                } while (objectListing.isTruncated());
            } else {
                // 复制文件
                copyObjectFile(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
                copiedList.add(destinationKey);
            }
            return copiedList;
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        } finally {
            baseOssService.removeObjectNameLock(sourceBucketName);
            baseOssService.removeObjectNameLock(destinationBucketName);
        }
        return copiedList;
    }

    private void copyObjectFile(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        baseOssService.printOperation(getPlatform().getKey(), "copyObject start" + "destinationKey: " + destinationKey, "sourceKey:" + sourceKey);
        ObjectMetadata objectMetadata = ossClient.getObjectMetadata(sourceBucketName, sourceKey);
        // 获取被拷贝文件的大小。
        long contentLength = objectMetadata.getContentLength();

        if (contentLength < 1024 * 1024 * 10L) {
            // 小文件执行普通拷贝
            ossClient.copyObject(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
        }

        // 设置分片大小为10 MB。单位为字节。
        long partSize = 1024 * 1024 * 10L;

        // 计算分片总数。
        int partCount = (int) (contentLength / partSize);
        if (contentLength % partSize != 0) {
            partCount++;
        }

        // 初始化拷贝任务。可以通过InitiateMultipartUploadRequest指定目标文件元信息。
        InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(destinationBucketName, destinationKey);
        InitiateMultipartUploadResult initiateMultipartUploadResult = ossClient.initiateMultipartUpload(initiateMultipartUploadRequest);
        String uploadId = initiateMultipartUploadResult.getUploadId();

        // 分片拷贝。
        List<PartETag> partETags = new ArrayList<>();
        for (int i = 0; i < partCount; i++) {
            // 计算每个分片的大小。
            long skipBytes = partSize * i;
            long size = Math.min(partSize, contentLength - skipBytes);
            // 创建UploadPartCopyRequest。可以通过UploadPartCopyRequest指定限定条件。
            UploadPartCopyRequest uploadPartCopyRequest = new UploadPartCopyRequest(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
            uploadPartCopyRequest.setUploadId(uploadId);
            uploadPartCopyRequest.setPartSize(size);
            uploadPartCopyRequest.setBeginIndex(skipBytes);
            uploadPartCopyRequest.setPartNumber(i + 1);
            UploadPartCopyResult uploadPartCopyResult = ossClient.uploadPartCopy(uploadPartCopyRequest);
            // 将返回的分片ETag保存到partETags中。
            partETags.add(uploadPartCopyResult.getPartETag());
        }
        // 提交分片拷贝任务。
        CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest(
                destinationBucketName, destinationKey, uploadId, partETags);
        ossClient.completeMultipartUpload(completeMultipartUploadRequest);
        baseOssService.printOperation(getPlatform().getKey(), "copyObject complete" + "destinationKey: " + destinationKey, "sourceKey:" + sourceKey);
    }

    @Override
    public String getPresignedObjectUrl(String objectName, int expiryTime) {
        try {
            Date expirationDate = new Date(System.currentTimeMillis() + expiryTime * 1000L);
            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET);
            generatePresignedUrlRequest.setExpiration(expirationDate);
            // 生成以GET方法访问的签名URL，访客可以直接通过浏览器访问相关内容。
            return ossClient.generatePresignedUrl(generatePresignedUrlRequest).toString();
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return null;
    }

    @Override
    public String getPresignedPutUrl(String objectName, String contentType, int expiryTime) {
        try {
            Date expirationDate = new Date(System.currentTimeMillis() + expiryTime * 1000L);
            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectName, com.aliyun.oss.HttpMethod.PUT);
            generatePresignedUrlRequest.setExpiration(expirationDate);
            if (contentType != null) {
                generatePresignedUrlRequest.setContentType(contentType);
            }
            return ossClient.generatePresignedUrl(generatePresignedUrlRequest).toString();
        } catch (OSSException oe) {
            log.error(oe.getMessage(), oe);
        } catch (ClientException ce) {
            log.error(ce.getMessage(), ce);
        }
        return null;
    }

    @Override
    public Map<Integer, String> getPresignedUploadPartUrls(String objectName, String uploadId, int totalParts, int expiryTime) {
        Map<Integer, String> urlMap = new HashMap<>(totalParts);
        for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
            try {
                Date expirationDate = new Date(System. currentTimeMillis() + expiryTime * 1000L);
                Map<String, String> params = new LinkedHashMap<>();
                params.put("uploadId", uploadId);
                params.put("partNumber", String.valueOf(partNumber));

                GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.PUT);
                request.setExpiration(expirationDate);
                request.setQueryParameter(params);
                String url = ossClient.generatePresignedUrl(request).toString();
                if (url != null) {
                    urlMap.put(partNumber, url);
                }
            } catch (Exception e) {
                log.error("Error generating presigned upload part URL for: {}", objectName, e);
                return null;
            }
        }
        return urlMap;
    }

    @Override
    public void clearCache(String objectName) {
        baseOssService.clearCache(objectName);
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
    public void close() {
        baseOssService.closePrint();
        if (this.ossClient != null) {
            this.ossClient.shutdown();
        }
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }
}
