package com.jmal.clouddisk.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 阅后即焚笔记响应 DTO
 */
@Data
@AllArgsConstructor
@Schema(description = "阅后即焚笔记响应")
public class BurnNoteResponseDTO {

    @Schema(description = "加密后的内容")
    private String encryptedContent;

    @Schema(description = "是否为文件类型")
    private Boolean isFile;
}
