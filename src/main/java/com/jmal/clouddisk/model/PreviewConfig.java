package com.jmal.clouddisk.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "previewConfig")
@Schema(name = "previewConfig", title = "预览配置")
public class PreviewConfig {
    @Id
    private String id;

    @Schema(name = "iframe", title = "iframe预览配置")
    private String iframe;
}
