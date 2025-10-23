package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;

@Data
public class Metadata implements Reflective {
    String filepath;
    String filename;
    String time;
    String charset;
    String compression;
    /**
     * 操作人(username)
     */
    String operator;
    Long size;
}
