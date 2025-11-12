package com.jmal.clouddisk.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件上传进度 DTO
 */
@Data
@Schema(description = "文件上传进度")
public class BurnNoteProgressDTO {

    @Schema(description = "已上传的分片数")
    private Integer uploadedChunks;

    @Schema(description = "总分片数")
    private Integer totalChunks;

    @Schema(description = "是否完成")
    private Boolean complete;

}
