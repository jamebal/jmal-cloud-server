package com.jmal.clouddisk.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建阅后即焚笔记")
public class BurnNoteCreateDTO {

    @Schema(description = "加密后的内容")
    private String encryptedContent;

    @Schema(description = "是否为文件类型")
    private Boolean isFile = false;

    @Schema(description = "文件分片总数（仅文件类型）")
    private Integer totalChunks;

    @Schema(description = "文件原始大小（字节）")
    private Long fileSize;

    @Schema(description = "查看次数限制")
    private Integer views;

    @Schema(description = "过期时间（分钟）")
    private Integer expirationMinutes;
}
