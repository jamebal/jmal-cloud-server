package com.jmal.clouddisk.oss.tencent;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSException;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.Copy;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
public class TencentOssService implements IOssService {

    private final String bucketName;

    private final COSClient cosClient;

    private final BaseOssService baseOssService;

    private final Region region;

    private final TransferManager transferManager;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public TencentOssService(FileProperties fileProperties, OssConfigDTO ossConfigDTO) {
        // 创建COSClient实例。
        String accessKeyId = ossConfigDTO.getAccessKey();
        String accessKeySecret = ossConfigDTO.getSecretKey();
        this.region = new Region(ossConfigDTO.getRegion());
        this.bucketName = ossConfigDTO.getBucket();
        COSCredentials cred = new BasicCOSCredentials(accessKeyId, accessKeySecret);
        ClientConfig clientConfig = new ClientConfig(region);
        clientConfig.setHttpProtocol(HttpProtocol.https);
        this.cosClient = new COSClient(cred, clientConfig);
        scheduledThreadPoolExecutor = ThreadUtil.createScheduledExecutor(1);
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor, ossConfigDTO);
        ThreadUtil.execute(this::getMultipartUploads);
        this.transferManager = new TransferManager(cosClient);
        createTransferManager();
    }

    // 创建 TransferManager 实例，这个实例用来后续调用高级接口
    private void createTransferManager() {
        // 创建一个 COSClient 实例，这是访问 COS 服务的基础实例。
        // 这里创建的 cosClient 是以复制的目的端信息为基础的
        // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        // 传入一个 threadpool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        // 设置高级接口的配置项
        // 分块复制阈值和分块大小分别为 5MB 和 1MB
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartCopyThreshold(5L * 1024L * 1024L);
        transferManagerConfiguration.setMultipartCopyPartSize(1024L * 1024L);
        this.transferManager.setConfiguration(transferManagerConfiguration);
    }

    @Override
    public PlatformOSS getPlatform() {
        return PlatformOSS.TENCENT;
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
        COSObject ossObject = null;
        try {
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectName);
            if (rangeStart != null && rangeEnd != null) {
                getObjectRequest.setRange(rangeStart, rangeEnd);
            }
            ossObject = this.cosClient.getObject(getObjectRequest);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        if (ossObject == null) {
            return null;
        }
        return new TencentOssObject(ossObject, this);
    }

    @Override
    public boolean deleteObject(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteObject", objectName);
            cosClient.deleteObject(bucketName, objectName);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteDir(String objectName) {
        try {
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            // 设置 bucket 名称
            listObjectsRequest.setBucketName(bucketName);
            // prefix 表示列出的对象名以 prefix 为前缀
            // 这里填要列出的目录的相对 bucket 的路径
            listObjectsRequest.setPrefix(objectName);
            // 设置最大遍历出多少个对象, 一次 listobject 最大支持1000
            listObjectsRequest.setMaxKeys(1000);
            // 保存每次列出的结果
            ObjectListing objectListing;
            do {
                objectListing = cosClient.listObjects(listObjectsRequest);
                // 这里保存列出的对象列表
                List<COSObjectSummary> cosObjectSummaries = objectListing.getObjectSummaries();
                ArrayList<DeleteObjectsRequest.KeyVersion> delObjects = new ArrayList<>();
                for (COSObjectSummary cosObjectSummary : cosObjectSummaries) {
                    delObjects.add(new DeleteObjectsRequest.KeyVersion(cosObjectSummary.getKey()));
                }
                DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName);
                deleteObjectsRequest.setKeys(delObjects);
                cosClient.deleteObjects(deleteObjectsRequest);
                // 标记下一次开始的位置
                String nextMarker = objectListing.getNextMarker();
                listObjectsRequest.setMarker(nextMarker);
            } while (objectListing.isTruncated());
            return true;
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public List<FileInfo> getFileInfoList(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            baseOssService.printOperation(getPlatform().getKey(), "getFileInfoList", objectName);
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            // 设置 bucket 名称
            listObjectsRequest.setBucketName(bucketName);
            // prefix 表示列出的对象名以 prefix 为前缀
            // 这里填要列出的目录的相对 bucket 的路径
            listObjectsRequest.setPrefix(objectName);
            // delimiter 表示目录的截断符, 例如：设置为 / 则表示对象名遇到 / 就当做一级目录）
            listObjectsRequest.setDelimiter("/");
            // 设置最大遍历出多少个对象, 一次 listobject 最大支持1000
            listObjectsRequest.setMaxKeys(1000);
            // 保存每次列出的结果
            ObjectListing objectListing;
            do {
                objectListing = cosClient.listObjects(listObjectsRequest);
                // 对象列表
                List<COSObjectSummary> cosObjectSummaries = objectListing.getObjectSummaries();
                for (COSObjectSummary objectSummary : cosObjectSummaries) {
                    S3ObjectSummary s3ObjectSummary = new S3ObjectSummary(objectSummary.getSize(), objectSummary.getKey(), objectSummary.getETag(), objectSummary.getLastModified(), objectSummary.getBucketName());
                    baseOssService.addFileInfoList(objectName, fileInfoList, s3ObjectSummary);
                }
                // 子目录
                for (String commonPrefix : objectListing.getCommonPrefixes()) {
                    fileInfoList.add(baseOssService.newFileInfo(commonPrefix));
                }
                // 标记下一次开始的位置
                String nextMarker = objectListing.getNextMarker();
                listObjectsRequest.setMarker(nextMarker);
            } while (objectListing.isTruncated());
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
        return fileInfoList;
    }

    @Override
    public List<FileInfo> getAllObjectsWithPrefix(String objectName) {
        // 列举所有包含指定前缀的所有文件
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            // 设置 bucket 名称
            listObjectsRequest.setBucketName(bucketName);
            // prefix 表示列出的对象名以 prefix 为前缀
            // 这里填要列出的目录的相对 bucket 的路径
            listObjectsRequest.setPrefix(objectName);
            // 设置最大遍历出多少个对象, 一次 listObject 最大支持1000
            listObjectsRequest.setMaxKeys(1000);
            // 保存每次列出的结果
            ObjectListing objectListing;
            do {
                objectListing = cosClient.listObjects(listObjectsRequest);
                // 这里保存列出的对象列表
                List<COSObjectSummary> cosObjectSummaries = objectListing.getObjectSummaries();
                cosObjectSummaries.parallelStream().forEach(cosObjectSummary -> fileInfoList.add(new FileInfo(cosObjectSummary.getKey(), cosObjectSummary.getETag(), cosObjectSummary.getSize(), cosObjectSummary.getLastModified())));
                // 标记下一次开始的位置
                String nextMarker = objectListing.getNextMarker();
                listObjectsRequest.setMarker(nextMarker);
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
        exist = this.cosClient.doesBucketExist(bucketName);
        if (exist) {
            this.cosClient.getBucketAcl(bucketName);
        }
        return exist;
    }

    @Override
    public boolean doesObjectExist(String objectName) {
        return this.cosClient.doesObjectExist(bucketName, objectName);
    }

    @Override
    public String getUploadId(String objectName) {
        return baseOssService.getUploadId(objectName);
    }

    @Override
    public String initiateMultipartUpload(String objectName) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectName);
        // 分块上传的过程中，仅能通过初始化分块指定文件上传之后的 metadata
        // 需要的头部可以在这里指定
        ObjectMetadata objectMetadata = new ObjectMetadata();
        request.setObjectMetadata(objectMetadata);
        InitiateMultipartUploadResult initResult = cosClient.initiateMultipartUpload(request);
        // 获取 uploadId
        return initResult.getUploadId();
    }

    private void getMultipartUploads() {
        try {
            ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(bucketName);
            // 每次请求最多列出多少个
            listMultipartUploadsRequest.setMaxUploads(100);
            MultipartUploadListing multipartUploadListing;
            do {
                multipartUploadListing = cosClient.listMultipartUploads(listMultipartUploadsRequest);
                List<MultipartUpload> multipartUploads = multipartUploadListing.getMultipartUploads();
                for (MultipartUpload mUpload : multipartUploads) {
                    log.info("{}, 碎片文件: objectName: {}, uploadId: {}", getPlatform().getValue(), mUpload.getKey(), mUpload.getUploadId());
                    baseOssService.setUpdateIdCache(mUpload.getKey(), mUpload.getUploadId());
                }
                listMultipartUploadsRequest.setKeyMarker(multipartUploadListing.getNextKeyMarker());
                listMultipartUploadsRequest.setUploadIdMarker(multipartUploadListing.getNextUploadIdMarker());
            } while (multipartUploadListing.isTruncated());
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public CopyOnWriteArrayList<Integer> getListParts(String objectName, String uploadId) {
        return new CopyOnWriteArrayList<>(getPartETagList(objectName, uploadId).stream().map(PartETag::getPartNumber).toList());
    }

    private List<PartETag> getPartETagList(String objectName, String uploadId) {
        List<PartETag> partETagList = new ArrayList<>();
        try {
            PartListing partListing;
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            do {
                partListing = cosClient.listParts(listPartsRequest);
                for (PartSummary partSummary : partListing.getParts()) {
                    partETagList.add(new PartETag(partSummary.getPartNumber(), partSummary.getETag()));
                }
                listPartsRequest.setPartNumberMarker(partListing.getNextPartNumberMarker());
            } while (partListing.isTruncated());
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
        return partETagList;
    }

    @Override
    public boolean uploadPart(InputStream inputStream, String objectName, int partSize, int partNumber, String uploadId) {
        UploadPartRequest uploadPartRequest = new UploadPartRequest();
        uploadPartRequest.setBucketName(bucketName);
        uploadPartRequest.setKey(objectName);
        uploadPartRequest.setUploadId(uploadId);
        uploadPartRequest.setInputStream(inputStream);
        // 设置分块的长度
        uploadPartRequest.setPartSize(partSize);
        // 设置要上传的分块编号
        uploadPartRequest.setPartNumber(partNumber);
        try {
            this.cosClient.uploadPart(uploadPartRequest);
            return true;
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        AbortMultipartUploadRequest abortMultipartUploadRequest = new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
        try {
            cosClient.abortMultipartUpload(abortMultipartUploadRequest);
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void completeMultipartUpload(String objectName, String uploadId, Long totalSize) {
        baseOssService.printOperation(getPlatform().getKey(), "completeMultipartUpload", objectName);
        // 查询已上传的分片
        List<PartETag> partETags = getPartETagList(objectName, uploadId);
        // 完成分片上传
        CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partETags);
        cosClient.completeMultipartUpload(completeMultipartUploadRequest);
        baseOssService.onUploadSuccess(objectName, totalSize);
    }

    @Override
    public void getThumbnail(String objectName, File file, int width) {
        GetObjectRequest request = new GetObjectRequest(bucketName, objectName);
        // 指定目标图片宽度为 Width，高度等比缩放
        String rule = "imageMogr2/thumbnail/" + width + "x";
        request.putCustomQueryParameter(rule, null);
        cosClient.getObject(request, file);
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
                ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
                // 设置 bucket 名称
                listObjectsRequest.setBucketName(bucketName);
                // prefix 表示列出的对象名以 prefix 为前缀
                // 这里填要列出的目录的相对 bucket 的路径
                listObjectsRequest.setPrefix(sourceKey);
                // 设置最大遍历出多少个对象, 一次 listobject 最大支持1000
                listObjectsRequest.setMaxKeys(1000);
                // 保存每次列出的结果
                ObjectListing objectListing;
                do {
                    objectListing = cosClient.listObjects(listObjectsRequest);
                    // 这里保存列出的对象列表
                    List<COSObjectSummary> cosObjectSummaries = objectListing.getObjectSummaries();
                    cosObjectSummaries.parallelStream().forEach(cosObjectSummary -> {
                        String destKey = destinationKey + cosObjectSummary.getKey().substring(sourceKey.length());
                        copyObjectFile(cosObjectSummary.getBucketName(), cosObjectSummary.getKey(), destinationBucketName, destKey);
                    });
                    // 标记下一次开始的位置
                    String nextMarker = objectListing.getNextMarker();
                    listObjectsRequest.setMarker(nextMarker);
                } while (objectListing.isTruncated());
            } else {
                // 复制文件
                copyObjectFile(sourceBucketName, sourceKey, destinationBucketName, destinationKey);
            }
            return true;
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        } finally {
            baseOssService.removeObjectNameLock(sourceBucketName);
            baseOssService.removeObjectNameLock(destinationBucketName);
        }
        return false;
    }

    public void copyObjectFile(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(region, sourceBucketName, sourceKey, destinationBucketName, destinationKey);
        try {
            baseOssService.printOperation(getPlatform().getKey(), "copyObject start" + "destinationKey: " + destinationKey, "sourceKey:" + sourceKey);
            Copy copy = transferManager.copy(copyObjectRequest);
            // 高级接口会返回一个异步结果 Copy
            // 可同步的调用 waitForCopyResult 等待复制结束, 成功返回 CopyResult, 失败抛出异常
            copy.waitForCopyResult();
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public FileInfo newFolder(String objectName) {
        baseOssService.printOperation(getPlatform().getKey(), "mkdir", objectName);
        if (!objectName.endsWith("/")) {
            objectName = objectName + "/";
        }
        try {
            // 这里创建一个空的 ByteArrayInputStream 来作为示例
            InputStream inputStream = new ByteArrayInputStream(new byte[0]);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(0);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream, objectMetadata);
            cosClient.putObject(putObjectRequest);
            return baseOssService.newFileInfo(objectName);
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
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
            // 创建PutObject请求。
            PutObjectResult putObjectResult = this.cosClient.putObject(putObjectRequest);
            if (putObjectResult.getRequestId() != null) {
                // 上传成功
                baseOssService.onUploadSuccess(objectName, tempFileAbsolutePath);
            }
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void uploadFile(InputStream inputStream, String objectName, long inputStreamLength) {
        baseOssService.printOperation(getPlatform().getKey(), "uploadFile inputStream", objectName);
        ObjectMetadata objectMetadata = new ObjectMetadata();
        // 上传的流如果能够获取准确的流长度，则推荐一定填写 content-length
        // 如果确实没办法获取到，则下面这行可以省略，但同时高级接口也没办法使用分块上传了
        objectMetadata.setContentLength(inputStreamLength);
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream, objectMetadata);
        putObjectRequest.setStorageClass(StorageClass.Standard);
        try {
            cosClient.putObject(putObjectRequest);
            baseOssService.onUploadSuccess(objectName, inputStreamLength);
        } catch (CosClientException e) {
            log.error(e.getMessage(), e);
        }
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
        if (this.cosClient != null) {
            this.cosClient.shutdown();
        }
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }
}
