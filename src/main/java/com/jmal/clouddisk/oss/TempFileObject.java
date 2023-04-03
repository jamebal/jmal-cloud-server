package com.jmal.clouddisk.oss;

import java.io.*;

/**
 * @author jmal
 * @Description TempFileObject
 * @date 2023/4/3 14:03
 */
public class TempFileObject extends AbstractOssObject {

    private final File tempFile;

    public TempFileObject(File file) {
        this.tempFile = file;
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
    public long getContentLength() {
        return tempFile.length();
    }

    @Override
    public void close() throws IOException {
        closeObject();
    }
}
