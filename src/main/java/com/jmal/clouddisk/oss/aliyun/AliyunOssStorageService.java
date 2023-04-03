package com.jmal.clouddisk.oss.aliyun;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.lang.Console;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AliyunOssStorageService implements IOssStorageService, DisposableBean {

    private static String endpoint = "https://oss-cn-guangzhou.aliyuncs.com";
    private static final String accessKeyId = "";
    private static final String accessKeySecret = "";
    private static final String bucketName = "jmalcloud";

    private final OSS ossClient;

    private final OssStorageBase ossStorageBase;


    public AliyunOssStorageService(FileProperties fileProperties) {
        // 创建OSSClient实例。
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        this.ossStorageBase = new OssStorageBase(this, bucketName, fileProperties);
    }

    @Override
    public PlatformOSS getPlatform() {
        return PlatformOSS.ALIYUN;
    }

    @Override
    public FileInfo getFileInfo(String objectName) {
        return ossStorageBase.getFileInfo(objectName);
    }

    @Override
    public boolean delete(String objectName) {
        boolean deleted;
        if (OssStorageBase.deleteCache(objectName)) return true;
        if (objectName.endsWith("/")) {
            // 删除目录
            deleted = deleteDir(objectName);
        } else {
            // 删除文件
            deleted = deleteObject(objectName);
        }
        if (deleted) {
            ossStorageBase.afterDeleteCache(objectName);
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
    public String[] list(String objectName) {
        return ossStorageBase.getFileNameList(objectName).toArray(new String[0]);
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
                    FileInfo fileInfo = ossStorageBase.getFileInfoCache(objectName);
                    if (fileInfo == null) {
                        OssStorageBase.setFileInfoCache(objectName, ossStorageBase.newFileInfo(objectName));
                    }
                }
            }

            // commonPrefixs列表中显示的是fun目录下的所有子文件夹。由于fun/movie/001.avi和fun/movie/007.avi属于fun文件夹下的movie目录，因此这两个文件未在列表中。
            for (String commonPrefix : listing.getCommonPrefixes()) {
                fileInfoList.add(ossStorageBase.newFileInfo(commonPrefix));
            }
        } catch (OSSException oe) {
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
        for (FileInfo fileInfo : fileInfoList) {
            Console.error(fileInfo);
        }
        return fileInfoList;
    }

    private static FileInfo getFileInfo(OSSObjectSummary objectSummary) {
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
        Path path = OssStorageBase.getTempFileCache(objectName);
        if (path != null) {
            return new TempFileObject(path.toFile());
        }
        printOperation(getPlatform().getKey(), "getObject", objectName);
        // 创建OSSClient实例。
        return new AliyunOssObject(getAliyunOssObject(objectName));
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
            OssStorageBase.setFileInfoCache(objectName, fileInfo);
            OssStorageBase.fileInfoListCache.invalidateAll();
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
            return ossStorageBase.newFileInfo(objectName);
        } catch (OSSException oe) {
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        }
        return null;
    }

    @Override
    public boolean write(InputStream inputStream, String ossPath, String objectName) {
        return ossStorageBase.writeObject(inputStream, ossPath, objectName);
    }

    @Override
    public void uploadFile(Path tempFileAbsolutePath, String objectName) {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                executeUpload(tempFileAbsolutePath, objectName);
            }
        };
        // 在10秒后执行上传
        timer.schedule(task, 10000);
    }

    private void executeUpload(Path tempFileAbsolutePath, String objectName) {
        try {
            TimeUnit.SECONDS.sleep(10);
            if (!PathUtil.exists(tempFileAbsolutePath, false)) {
                return;
            }
            printOperation(getPlatform().getKey(), "upload", objectName);
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, tempFileAbsolutePath.toFile());
            // 设置该属性可以返回response。如果不设置，则返回的response为空。
            putObjectRequest.setProcess("true");
            // 创建PutObject请求。
            this.ossClient.putObject(putObjectRequest);
            OssStorageBase.clearTempFileCache(objectName);
            OssStorageBase.setFileInfoCache(objectName, ossStorageBase.newFileInfo(objectName, tempFileAbsolutePath.toFile()));
        } catch (OSSException oe) {
            printOSSException(oe);
        } catch (ClientException ce) {
            printClientException(ce);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
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
        Console.log(platform, operation, objectName);
    }

    @Override
    public void destroy() {
        if (this.ossClient != null) {
            this.ossClient.shutdown();
        }
    }
}
