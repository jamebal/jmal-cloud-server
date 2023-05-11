package com.jmal.clouddisk.oss.minio;

import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import io.minio.GetObjectResponse;
import io.minio.StatObjectResponse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * @author jmal
 * @Description MinIOOssObject
 * @date 2023/4/23 16:33
 */
public class MinIOObject extends AbstractOssObject {

    private final StatObjectResponse statObjectResponse;
    private final GetObjectResponse getObjectResponse;

    private final IOssService ossService;

    public MinIOObject(StatObjectResponse statObjectResponse, GetObjectResponse getObjectResponse, IOssService ossService) {
        this.statObjectResponse = statObjectResponse;
        this.getObjectResponse = getObjectResponse;
        this.ossService = ossService;
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException {
        return getObjectResponse;
    }

    @Override
    public void closeObject() throws IOException {
        this.getObjectResponse.close();
    }

    @Override
    public IOssService getOssService() {
        return this.ossService;
    }

    @Override
    public String getKey() {
        return this.statObjectResponse.object();
    }

    @Override
    public FileInfo getFileInfo() {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(this.statObjectResponse.size());
        fileInfo.setKey(this.statObjectResponse.object());
        fileInfo.setLastModified(Date.from(this.statObjectResponse.lastModified().toInstant()));
        fileInfo.setETag(this.statObjectResponse.etag());
        fileInfo.setBucketName(this.statObjectResponse.bucket());
        return fileInfo;
    }

    @Override
    public long getContentLength() {
        return this.statObjectResponse.size();
    }

    @Override
    public void close() throws IOException {
        closeObject();
    }
}
