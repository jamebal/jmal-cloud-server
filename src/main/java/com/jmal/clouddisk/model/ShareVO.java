package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 文件分享模型
 * @Author jmal
 * @Date 2020-03-17 16:28
 */
@Data
public class ShareVO implements Reflective {
    private String shareId;
    private String shortId;
    /***
     * 提取码
     */
    private String extractionCode;

    private Boolean isPrivacy;

    private String userId;

    private String fileName;

    private Boolean shareBase;
    private Boolean subShare;

    private Boolean isFolder;
    private String contentType;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime expireDate;

    private List<OperationPermission> operationPermissionList;

}
