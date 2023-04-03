package com.jmal.clouddisk.oss.aliyun;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Console;
import com.aliyun.oss.model.OSSObject;
import com.jmal.clouddisk.oss.AbstractOssObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

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
    public long getContentLength() {
        return this.ossObject.getObjectMetadata().getContentLength();
    }

    @Override
    public void close() throws IOException {
        closeObject();
    }
}
