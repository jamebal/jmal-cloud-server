package com.jmal.clouddisk.model;

import lombok.Data;

@Data
public class Metadata {
    String filepath;
    String filename;
    String time;
    String compression;
    /**
     * 操作人(username)
     */
    String operator;
    Long size;
}
