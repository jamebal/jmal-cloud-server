package com.jmal.clouddisk.oss;

import lombok.Data;

import java.util.Date;

/**
 * @author jmal
 * @Description S3ObjectSummary
 * @date 2023/4/6 11:08
 */
@Data
public class S3ObjectSummary {
    private String bucketName;
    private String key;
    private String eTag;
    private long size;
    private Date lastModified;

    public S3ObjectSummary(long size, String key, String eTag, Date lastModified, String bucketName) {
        this.size = size;
        this.key = key;
        this.eTag = eTag;
        this.lastModified = lastModified;
        this.bucketName = bucketName;
    }
}
