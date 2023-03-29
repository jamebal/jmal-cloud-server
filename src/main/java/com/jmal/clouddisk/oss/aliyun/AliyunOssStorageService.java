package com.jmal.clouddisk.oss.aliyun;

import cn.hutool.core.lang.Console;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssStorageService;
import com.jmal.clouddisk.oss.PlatformOSS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Lazy
@Slf4j
public class AliyunOssStorageService implements IOssStorageService, DisposableBean {
    private static String endpoint = "https://oss-cn-guangzhou.aliyuncs.com";
    private static final String accessKeyId = "";
    private static final String accessKeySecret = "";
    private static final String bucketName = "jmalcloud";

    private final OSS ossClient;

    public AliyunOssStorageService() {
        // 创建OSSClient实例。
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    @Override
    public PlatformOSS getPlatform() {
        return PlatformOSS.ALIYUN;
    }

    @Override
    public String getBucketName() {
        return bucketName;
    }

    @Override
    public FileInfo getFileInfo(String objectName) {
        Console.log("AliyunOSSStorageService", "getFileInfo", objectName);
        try{
            if (objectName.endsWith("/")) {
                List<FileInfo> fileInfoList = fileInfoList(objectName, true);
                if (!fileInfoList.isEmpty()) {
                    return fileInfoList.get(0);
                }
            } else {
                OSSObject object = this.ossClient.getObject(bucketName, objectName);
                if (object != null) {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setSize(object.getObjectMetadata().getContentLength());
                    fileInfo.setKey(object.getKey());
                    fileInfo.setETag(object.getObjectMetadata().getETag());
                    fileInfo.setLastModified(object.getObjectMetadata().getLastModified());
                    fileInfo.setBucketName(bucketName);
                    return fileInfo;
                }
            }
        }catch (Exception e) {
            return null;
        }
        return null;
    }

    @Override
    public boolean delete(String objectName) {
        Console.log("AliyunOSSStorageService", "delete", objectName);
        if (objectName.endsWith("/")) {
            // 删除目录
            return deleteDir(objectName);
        } else {
            // 删除文件
            ossClient.deleteObject(bucketName, objectName);
            return true;
        }
    }

    private boolean deleteDir(String objectName) {
        try {
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
                    DeleteObjectsResult deleteObjectsResult = ossClient.deleteObjects(deleteObjectsRequest);
                    deleteObjectsResult.getDeletedObjects();
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
    public List<FileInfo> fileInfoList(String path) {
        return fileInfoList(path, false);
    }

    private List<FileInfo> fileInfoList(String path, boolean onlySelf) {
        Console.log("AliyunOSSStorageService", "fileInfoList", path);
        List<FileInfo> fileInfoList = new ArrayList<>();
        try {
            // 构造ListObjectsRequest请求。
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName);

            // 设置正斜线（/）为文件夹的分隔符。
            listObjectsRequest.setDelimiter("/");

            // 列出fun目录下的所有文件和文件夹。
            listObjectsRequest.setPrefix(path);

            ObjectListing listing = ossClient.listObjects(listObjectsRequest);

            // objectSummaries的列表中给出的是fun目录下的文件。
            for (OSSObjectSummary objectSummary : listing.getObjectSummaries()) {
                if (onlySelf) {
                    if (objectSummary.getKey().equals(path)) {
                        FileInfo fileInfo = new FileInfo();
                        fileInfo.setSize(objectSummary.getSize());
                        fileInfo.setKey(objectSummary.getKey());
                        fileInfo.setETag(objectSummary.getETag());
                        fileInfo.setLastModified(objectSummary.getLastModified());
                        fileInfo.setBucketName(bucketName);
                        fileInfoList.add(fileInfo);
                        return fileInfoList;
                    }
                } else {
                    if (!objectSummary.getKey().equals(path)) {
                        FileInfo fileInfo = new FileInfo();
                        fileInfo.setSize(objectSummary.getSize());
                        fileInfo.setKey(objectSummary.getKey());
                        fileInfo.setETag(objectSummary.getETag());
                        fileInfo.setLastModified(objectSummary.getLastModified());
                        fileInfo.setBucketName(bucketName);
                        fileInfoList.add(fileInfo);
                    }
                }
            }

            // commonPrefixs列表中显示的是fun目录下的所有子文件夹。由于fun/movie/001.avi和fun/movie/007.avi属于fun文件夹下的movie目录，因此这两个文件未在列表中。
            for (String commonPrefix : listing.getCommonPrefixes()) {
                FileInfo fileInfo = new FileInfo();
                fileInfo.setSize(0);
                fileInfo.setKey(commonPrefix);
                fileInfo.setLastModified(new Date());
                fileInfo.setBucketName(bucketName);
                fileInfoList.add(fileInfo);
            }
        } catch (OSSException oe) {
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
        Console.error(fileInfoList);
        return fileInfoList;
    }

    @Override
    public AbstractOssObject getObject(String objectName) {
        Console.log("AliyunOSSStorageService", "getObject", objectName);
        try {
            // 创建OSSClient实例。
            return new AliyunOssObject(this.ossClient.getObject(bucketName, objectName));
        } catch (OSSException e) {
            return null;
        }
    }

    @Override
    public FileInfo mkdir(String objectName) {
        Console.log("AliyunOSSStorageService", "mkdir", objectName);
        if (!objectName.endsWith("/")) {
            objectName = objectName + "/";
        }
        try {
            String content = "";
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, new ByteArrayInputStream(content.getBytes()));
            // 如果需要上传时设置存储类型和访问权限，请参考以下示例代码。
            // ObjectMetadata metadata = new ObjectMetadata();
            // metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
            // metadata.setObjectAcl(CannedAccessControlList.Private);
            // putObjectRequest.setMetadata(metadata);
            // 上传字符串。
            ossClient.putObject(putObjectRequest);
            return newFolderFileInfo(objectName);
        } catch (OSSException oe) {
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
        return null;
    }

    private FileInfo newFolderFileInfo(String objectName) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(0);
        fileInfo.setKey(objectName);
        fileInfo.setLastModified(new Date());
        fileInfo.setBucketName(bucketName);
        return fileInfo;
    }

    @Override
    public void writeObject(InputStream inputStream, String objectName) {
        Console.log("AliyunOSSStorageService", "writeObject", objectName);
        try {
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream);
            // 设置该属性可以返回response。如果不设置，则返回的response为空。
            putObjectRequest.setProcess("true");
            // 创建PutObject请求。
            ossClient.putObject(putObjectRequest);
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

    @Override
    public void destroy() {
        if (this.ossClient != null) {
            this.ossClient.shutdown();
        }
    }
}
