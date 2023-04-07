package com.jmal.clouddisk.oss.tencent;

import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.qcloud.cos.model.COSObject;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author jmal
 * @Description TencentOssObject
 * @date 2023/3/29 12:06
 */
public class TencentOssObject extends AbstractOssObject {

    private final COSObject cosObject;

    public TencentOssObject(COSObject cosObject) {
        this.cosObject = cosObject;
    }

    @Override
    public InputStream getInputStream() {
        return this.cosObject.getObjectContent();
    }

    @Override
    public void closeObject() throws IOException {
        this.cosObject.close();
    }

    @Override
    public String getKey() {
        return this.cosObject.getKey();
    }

    @Override
    public FileInfo getFileInfo() {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(this.cosObject.getObjectMetadata().getContentLength());
        fileInfo.setKey(this.cosObject.getKey());
        fileInfo.setLastModified(this.cosObject.getObjectMetadata().getLastModified());
        fileInfo.setETag(this.cosObject.getObjectMetadata().getETag());
        fileInfo.setBucketName(this.cosObject.getBucketName());
        return fileInfo;
    }

    @Override
    public long getContentLength() {
        return this.cosObject.getObjectMetadata().getContentLength();
    }

    @Override
    public void close() throws IOException {
        closeObject();
    }
}
