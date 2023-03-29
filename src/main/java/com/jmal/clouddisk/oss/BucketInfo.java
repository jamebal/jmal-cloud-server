package com.jmal.clouddisk.oss;

import lombok.Data;

import java.nio.file.Paths;

/**
 * @author jmal
 * @Description OSS BucketInfo
 * @date 2023/3/29 13:49
 */
@Data
public class BucketInfo {
    String bucketName;
    String folderName;
    String username;
    PlatformOSS platform;

    public String getWebPathPrefix() {
        return Paths.get(username, folderName).toString();
    }
}
