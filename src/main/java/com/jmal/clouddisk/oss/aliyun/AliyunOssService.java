package com.jmal.clouddisk.oss.aliyun;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
public class AliyunOssService implements IOssService, Closeable {

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
        this.baseOssService = new BaseOssService(this, bucketName, fileProperties, scheduledThreadPoolExecutor);
        log.info("{}配置加载成功, bucket: {}, username: {}, {}", getPlatform().getValue(), bucketName, ossConfigDTO.getUsername(), this.hashCode());
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
        boolean deleted;
        baseOssService.deleteTempFileCache(objectName);
        if (objectName.endsWith("/")) {
            // 删除目录
            deleted = deleteDir(objectName);
        } else {
            // 删除文件
            deleted = deleteObject(objectName);
        }
        if (deleted) {
            baseOssService.onDeleteSuccess(objectName);
        }
        return deleted;
    }

    public boolean deleteObject(String objectName) {
        try {
            printOperation(getPlatform().getKey(), "deleteObject", objectName);
            ossClient.deleteObject(bucketName, objectName);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private boolean deleteDir(String objectName) {
        try {
            printOperation(getPlatform().getKey(), "deleteDir", objectName);
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
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
        return false;
    }

    @Override
    public String[] list(String objectName) {
        return baseOssService.getFileNameList(objectName).toArray(new String[0]);
    }

    public List<FileInfo> getFileInfoList(String objectName) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            // 构造ListObjectsRequest请求。
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName);

            // 设置正斜线（/）为文件夹的分隔符。
            listObjectsRequest.setDelimiter("/");

            // 列出fun目录下的所有文件和文件夹。
            listObjectsRequest.setPrefix(objectName);
            printOperation(getPlatform().getKey(), "getFileInfoList", objectName);
            ObjectListing listing = ossClient.listObjects(listObjectsRequest);

            // objectSummaries的列表中给出的是fun目录下的文件。
            for (OSSObjectSummary objectSummary : listing.getObjectSummaries()) {
                if (!objectSummary.getKey().equals(objectName)) {
                    FileInfo fileInfo = getFileInfo(objectSummary);
                    fileInfoList.add(fileInfo);
                } else {
                    FileInfo fileInfo = baseOssService.getFileInfoCache(objectName);
                    if (fileInfo == null) {
                        baseOssService.setFileInfoCache(objectName, baseOssService.newFileInfo(objectName));
                    }
                }
            }

            // commonPrefixs列表中显示的是fun目录下的所有子文件夹。由于fun/movie/001.avi和fun/movie/007.avi属于fun文件夹下的movie目录，因此这两个文件未在列表中。
            for (String commonPrefix : listing.getCommonPrefixes()) {
                fileInfoList.add(baseOssService.newFileInfo(commonPrefix));
            }
        } catch (OSSException oe) {
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
        return fileInfoList;
    }

    private FileInfo getFileInfo(OSSObjectSummary objectSummary) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(objectSummary.getSize());
        fileInfo.setKey(objectSummary.getKey());
        fileInfo.setETag(objectSummary.getETag());
        fileInfo.setLastModified(objectSummary.getLastModified());
        fileInfo.setBucketName(bucketName);
        return fileInfo;
    }

    @Override
    public AbstractOssObject getObject(String objectName) {
        Path path = baseOssService.getTempFileCache(objectName);
        if (path != null) {
            return new TempFileObject(path.toFile());
        }
        printOperation(getPlatform().getKey(), "getObject", objectName);
        // 创建OSSClient实例。
        com.aliyun.oss.model.OSSObject ossObject = getAliyunOssObject(objectName);
        if (ossObject == null) {
            return null;
        }
        return new AliyunOssObject(ossObject);
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

    private com.aliyun.oss.model.OSSObject getAliyunOssObject(String objectName) {
        try {
            // 创建OSSClient实例。
            return this.ossClient.getObject(bucketName, objectName);
        } catch (OSSException e) {
            return null;
        }
    }

    @Override
    public boolean mkdir(String ossPath, String objectName) {
        FileInfo fileInfo = newFolder(objectName);
        if (fileInfo != null) {
            baseOssService.onMkdirSuccess(objectName, fileInfo);
        }
        return fileInfo != null;
    }

    private FileInfo newFolder(String objectName) {
        printOperation(getPlatform().getKey(), "mkdir", objectName);
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
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
        return null;
    }

    @Override
    public boolean write(InputStream inputStream, String ossPath, String objectName) {
        return baseOssService.writeTempFile(inputStream, ossPath, objectName);
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
            printOperation(getPlatform().getKey(), "upload", objectName);
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
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
    }

    private static void printClientException(ClientException ce) {
        log.error("Caught an ClientException, which means the client encountered a serious internal problem while trying to communicate with OSS, such as not being able to access the network.");
        log.error(ce.getMessage());
    }

    private static void printOSSException(OSSException oe) {
        log.error("Caught an OSSException, which means your request made it to OSS, but was rejected with an error response for some reason.");
        log.error("Error Message:" + oe.getErrorMessage());
        log.error("Error Code:" + oe.getErrorCode());
        log.error("Request ID:" + oe.getRequestId());
        log.error("Host ID:" + oe.getHostId());
    }

    private void printOperation(String platform, String operation, String objectName) {
        log.info("{}, {}, {}", platform, operation, objectName);
    }

    @Override
    public void close() {
        log.info("platform: {}, bucketName: {} shutdown... {}", getPlatform().getValue(), bucketName, this.hashCode());
        if (this.ossClient != null) {
            this.ossClient.shutdown();
        }
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }
}
