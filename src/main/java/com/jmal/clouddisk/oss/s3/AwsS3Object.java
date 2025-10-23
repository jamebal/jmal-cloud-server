package com.jmal.clouddisk.oss.s3;

import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class AwsS3Object extends AbstractOssObject {

    private final HeadObjectResponse headResponse;
    private final ResponseInputStream<GetObjectResponse> responseInputStream;
    private final IOssService ossService;
    private final String bucketName;
    private final String key;

    /**
     * 优化的构造函数，包含了 bucket 和 key 的上下文信息
     * @param headResponse 对象元数据响应
     * @param responseInputStream 对象内容输入流
     * @param ossService 关联的 OSS 服务实例
     * @param bucketName 对象所在的 bucket 名称
     * @param key 对象的 key (路径)
     */
    public AwsS3Object(HeadObjectResponse headResponse, ResponseInputStream<GetObjectResponse> responseInputStream, IOssService ossService, String bucketName, String key) {
        this.headResponse = headResponse;
        this.responseInputStream = responseInputStream;
        this.ossService = ossService;
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException {
        return this.responseInputStream;
    }

    @Override
    public void closeObject() throws IOException {
        if (this.responseInputStream != null) {
            this.responseInputStream.close();
        }
    }

    @Override
    public IOssService getOssService() {
        return this.ossService;
    }

    @Override
    public String getKey() {
        return this.key;
    }

    @Override
    public FileInfo getFileInfo() {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(this.headResponse.contentLength());
        fileInfo.setKey(this.key);
        fileInfo.setLastModified(Date.from(this.headResponse.lastModified()));
        // ETag 在 AWS SDK v2 中会包含双引号，需要移除
        String eTag = this.headResponse.eTag();
        if (eTag != null) {
            fileInfo.setETag(eTag.replace("\"", ""));
        }
        fileInfo.setBucketName(this.bucketName);
        return fileInfo;
    }

    @Override
    public long getContentLength() {
        return this.headResponse.contentLength();
    }

    @Override
    public void close() throws IOException {
        closeObject();
    }
}
