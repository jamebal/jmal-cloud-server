package com.jmal.clouddisk.oss;

import java.io.*;
import java.util.Date;

/**
 * @author jmal
 * @Description TempFileObject
 * @date 2023/4/3 14:03
 */
public class TempFileObject extends AbstractOssObject {

    private final File tempFile;

    private final String objectName;

    private final String bucketName;

    private final IOssService ossService;

    public TempFileObject(File file, String objectName, String bucketName, IOssService ossService) {
        this.tempFile = file;
        this.objectName = objectName;
        this.bucketName = bucketName;
        this.ossService = ossService;
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException {
        return new FileInputStream(this.tempFile);
    }

    @Override
    public void closeObject() {
        // none
    }

    @Override
    public String getKey() {
        return this.objectName;
    }

    @Override
    public IOssService getOssService() {
        return this.ossService;
    }

    @Override
    public FileInfo getFileInfo() {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(this.tempFile.length());
        fileInfo.setKey(this.objectName);
        fileInfo.setLastModified(new Date(tempFile.lastModified()));
        fileInfo.setETag(String.valueOf(tempFile.hashCode()));
        fileInfo.setBucketName(this.bucketName);
        return fileInfo;
    }

    @Override
    public long getContentLength() {
        return tempFile.length();
    }

    @Override
    public void close() throws IOException {
        closeObject();
    }
}
