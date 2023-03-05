package com.jmal.clouddisk.model;

import lombok.Data;

/**
 * @Description 文件分享模型
 * @Author jmal
 * @Date 2020-03-17 16:28
 */
@Data
public class ShareVO {
    private String shareId;
    /***
     * 提取码
     */
    private String extractionCode;

    private Boolean isPrivacy;

    private String userId;

    private String fileName;

    private Boolean isFolder;
    private String contentType;

}
