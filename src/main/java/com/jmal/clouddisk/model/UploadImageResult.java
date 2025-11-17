package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadImageResult implements Reflective {
    private String url;
    private String originalURL;
    private String fileId;
    private String filename;
    private String filepath;
}
