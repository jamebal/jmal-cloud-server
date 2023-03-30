package com.jmal.clouddisk.oss;

import java.io.Closeable;
import java.io.InputStream;

/**
 * @author jmal
 * @Description AbstractOssObject
 * @date 2023/3/29 12:03
 */
public abstract class AbstractOssObject implements Closeable {

    public abstract InputStream getInputStream();
}
