package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel
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
    @ApiModelProperty(name = "contentText", value = "markdown内容", required = true)
    String contentText;
    @ApiModelProperty(name = "currentDirectory", value = "当前目录,用户的网盘目录,如果为空则为'/'")
    String currentDirectory;
    @ApiModelProperty(name = "updateDate", value = "更新时间")
    LocalDateTime updateDate;
    @ApiModelProperty(name = "isAlonePage", value = "是否为独立页面")
    Boolean isAlonePage;
    @ApiModelProperty(name = "pageSort", value = "页面顺序")
    Integer pageSort;
    @ApiModelProperty(name = "isDraft", value = "是否为草稿")
    Boolean isDraft;
    @ApiModelProperty(name = "isRelease", value = "是否发布")
    Boolean isRelease;
    @ApiModelProperty(name = "cover", value = "文章封面")
    String cover;
    @ApiModelProperty(name = "cover", value = "文章缩略名")
    String slug;
    String[] categoryIds;
    String[] tagNames;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
}
