package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 文章(添加/修改)DTO
 * @Date 2020/12/9 10:23 上午
 */
@Schema
@Data
@Valid
public class ArticleParamDTO {

    @NotNull(message = "userId不能为空")
    String userId;
    @NotNull(message = "username不能为空")
    String username;
    @NotNull(message = "filename不能为空")
    String filename;
    String fileId;
    @NotNull(message = "contentText不能为空")
    @Schema(name = "contentText", title = "markdown内容", required = true)
    String contentText;
    @Schema(name = "html", title = "html内容")
    String html;
    @Schema(name = "currentDirectory", title = "当前目录,用户的网盘目录,如果为空则为'/'")
    String currentDirectory;
    @Schema(name = "updateDate", title = "更新时间")
    LocalDateTime updateDate;
    @Schema(name = "isAlonePage", title = "是否为独立页面")
    Boolean isAlonePage;
    @Schema(name = "pageSort", title = "页面顺序")
    Integer pageSort;
    @Schema(name = "isDraft", title = "是否为草稿")
    Boolean isDraft;
    @Schema(name = "isRelease", title = "是否发布")
    Boolean isRelease;
    @Schema(name = "cover", title = "文章封面")
    String cover;
    @Schema(name = "cover", title = "文章缩略名")
    String slug;
    @Schema(name = "categoryIds", title = "分类Id集合")
    String[] categoryIds;
    @Schema(name = "tagNames", title = "标签名称集合")
    String[] tagNames;
    @Schema(name = "uploadDate", title = "更新时间")
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
}
