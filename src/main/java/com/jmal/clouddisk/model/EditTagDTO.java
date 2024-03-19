package com.jmal.clouddisk.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema
@Valid
public class EditTagDTO {
    @NotNull(message = "文件id列表不能为空")
    @Schema(description = "要修改的文件id列表")
    List<String> fileIds;

    @NotNull(message = "标签列表不能为空")
    @Schema(description = "于文件id列表相关联的标签列表")
    List<TagDTO> tagList;

    @Schema(description = "要额外删除的标签id列表")
    List<String> removeTagIds;
}
