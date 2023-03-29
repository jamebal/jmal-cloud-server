package com.jmal.clouddisk.oss.aliyun;

import com.aliyun.oss.model.OSSObject;
import com.jmal.clouddisk.oss.AbstractOssObject;

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
    public void closeObject() throws IOException {
        this.ossObject.close();
    }

    @Override
    public InputStream getInputStream() {
        return this.ossObject.getObjectContent();
    }

    @Override
    public void close() throws IOException {
        this.ossObject.close();
    }
}
