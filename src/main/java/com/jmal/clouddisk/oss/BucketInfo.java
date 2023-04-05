package com.jmal.clouddisk.oss;

import com.jmal.clouddisk.webdav.MyWebdavServlet;
import lombok.Data;

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
        return MyWebdavServlet.getPathDelimiter(username, folderName);
    }

}
