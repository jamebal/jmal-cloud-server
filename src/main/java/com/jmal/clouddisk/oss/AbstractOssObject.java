package com.jmal.clouddisk.oss;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author jmal
 * @Description AbstractOssObject
 * @date 2023/3/29 12:03
 */
public abstract class AbstractOssObject implements Closeable {

    public abstract InputStream getInputStream() throws FileNotFoundException;

    public abstract void closeObject() throws IOException;

    public String getKey() {
        return "";
    }

    public abstract FileInfo getFileInfo();

    public abstract long getContentLength();
}
