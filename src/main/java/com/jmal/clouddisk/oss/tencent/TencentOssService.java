package com.jmal.clouddisk.oss.tencent;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
public class TencentOssService implements IOssService {

    private final String bucketName;

    private final COSClient cosClient;

    private final BaseOssService baseOssService;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public TencentOssService(FileProperties fileProperties, OssConfigDTO ossConfigDTO) {
        // 创建COSClient实例。
        String accessKeyId = ossConfigDTO.getAccessKey();
        String accessKeySecret = ossConfigDTO.getSecretKey();
        String region = ossConfigDTO.getRegion();
        this.bucketName = ossConfigDTO.getBucket();
        COSCredentials cred = new BasicCOSCredentials(accessKeyId, accessKeySecret);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        this.cosClient = new COSClient(cred, clientConfig);
        scheduledThreadPoolExecutor = ThreadUtil.createScheduledExecutor(1);
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor);
        log.info( "{}配置加载成功, bucket: {}, username: {}", getPlatform().getValue(), bucketName, ossConfigDTO.getUsername());
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
    public AbstractOssObject getObject(String objectName) {
        return baseOssService.getObject(objectName);
    }

    @Override
    public AbstractOssObject getAbstractOssObject(String objectName) {
        COSObject ossObject = null;
        try {
            ossObject = this.cosClient.getObject(new GetObjectRequest(bucketName, objectName));
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        if (ossObject == null) {
            return null;
        }
        return new TencentOssObject(ossObject);
    }

    @Override
    public boolean deleteObject(String objectName) {
        try {
            baseOssService.printOperation(getPlatform().getKey(), "deleteObject", objectName);
            cosClient.deleteObject(bucketName, objectName);
        } catch (Exception e) {
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
        } catch (CosServiceException e) {
            // 如果部分删除成功部分失败, 返回 MultiObjectDeleteException
            printException(e);
        }
        return false;
    }

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
        } catch (Exception oe) {
            printException(oe);
        }
        return fileInfoList;
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
        } catch (Exception oe) {
            printException(oe);
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
        } catch (Exception oe) {
            printException(oe);
        }
    }


    private void printException(Exception e) {
        log.error(getPlatform().getValue() + e.getMessage(), e);
    }

    @Override
    public void close() {
        log.info("platform: {}, bucketName: {} shutdown...", getPlatform().getValue(), bucketName);
        if (this.cosClient != null) {
            this.cosClient.shutdown();
        }
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }
}
