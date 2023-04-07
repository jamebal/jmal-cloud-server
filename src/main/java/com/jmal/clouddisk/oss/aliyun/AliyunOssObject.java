package com.jmal.clouddisk.oss.aliyun;

import com.aliyun.oss.model.OSSObject;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author jmal
 * @Description AliyunOssObject
 * @date 2023/3/29 12:06
 */
public class AliyunOssObject extends AbstractOssObject {

    private final OSSObject ossObject;

    public AliyunOssObject(OSSObject ossObject) {
        this.ossObject = ossObject;
    }

    @Override
    public InputStream getInputStream() {
        return this.ossObject.getObjectContent();
    }

    @Override
    public void closeObject() throws IOException {
        this.ossObject.forcedClose();
    }

    @Override
    public String getKey() {
        return this.ossObject.getKey();
    }

    @Override
    public FileInfo getFileInfo() {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(this.ossObject.getObjectMetadata().getContentLength());
        fileInfo.setKey(this.ossObject.getKey());
        fileInfo.setLastModified(this.ossObject.getObjectMetadata().getLastModified());
        fileInfo.setETag(this.ossObject.getObjectMetadata().getETag());
        fileInfo.setBucketName(this.ossObject.getBucketName());
        return fileInfo;
    }

    @Override
    public long getContentLength() {
        return this.ossObject.getObjectMetadata().getContentLength();
    }

    @Override
    public void close() throws IOException {
        closeObject();
    }
}
