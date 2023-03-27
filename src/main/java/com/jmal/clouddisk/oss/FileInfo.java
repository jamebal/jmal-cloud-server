package com.jmal.clouddisk.oss;

import lombok.Data;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

@Data
public class FileInfo {
    private String bucketName;
    private String key;
    private String eTag;
    private long size;
    private Date lastModified;

    public String getName() {
        Path path = Paths.get(key);
        return path.getFileName().toString();
    }

}
