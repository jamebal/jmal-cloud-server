package com.jmal.clouddisk.model;

import lombok.Data;

@Data
public class Metadata {
    String filepath;
    String filename;
    String time;
    String compression;
    Long size;
}
