package com.jmal.clouddisk.webdav.resource;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.jmal.clouddisk.oss.FileInfo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Lazy
@Slf4j
public class AliyunOSSStorageService {
    private static String endpoint = "https://oss-cn-guangzhou.aliyuncs.com";
    private static final String accessKeyId = "";
    private static final String accessKeySecret = "";
    private static final String bucketName = "jmalcloud";

    private OSS ossClient;

    public AliyunOSSStorageService() {
        // 创建OSSClient实例。
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    public boolean delete(String objectName) {
        // 删除文件或目录。如果要删除目录，目录必须为空。
        ossClient.deleteObject(bucketName, objectName);
        return true;
    }

    public List<FileInfo> fileInfoList(String path) {
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
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        }
        return fileInfoList;
    }

    public OSSObject getObject(String objectName) throws IOException {
        // 创建OSSClient实例。
        OSSObject ossObject = this.ossClient.getObject(bucketName, objectName);
        return ossObject;
    }

    @PreDestroy
    public void destroy() {
        if (this.ossClient != null) {
            this.ossClient.shutdown();
        }
    }
}
