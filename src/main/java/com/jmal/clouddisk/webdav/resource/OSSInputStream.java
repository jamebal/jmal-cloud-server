package com.jmal.clouddisk.webdav.resource;

import com.aliyun.oss.model.OSSObject;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

/**
 * @author jmal
 * @Description OSSInputStream
 * @date 2023/3/28 16:32
 */
public class OSSInputStream extends CheckedInputStream {

    @Getter
    @Setter
    private OSSObject ossObject;

    /**
     * Creates an input stream using the specified Checksum.
     *
     * @param in    the input stream
     * @param cksum the Checksum
     */
    public OSSInputStream(InputStream in, Checksum cksum) {
        super(in, cksum);
    }
}
