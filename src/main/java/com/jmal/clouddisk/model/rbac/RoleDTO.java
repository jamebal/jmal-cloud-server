package com.jmal.clouddisk.model.rbac;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.service.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 角色传输对象
 * @Author jmal
 * @Date 2021/1/7 7:41 下午
 */
@Data
@Valid
@Schema
public class RoleDTO {
    String id;
    @NotNull(message = "角色名称不能为空")
    @Schema(name = "name", title = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    String name;
    @NotNull(message = "角色标识不能为空")
    @Schema(name = "code", title = "角色标识", requiredMode = Schema.RequiredMode.REQUIRED)
    String code;
    @Schema(name = "remarks", title = "备注")
    String remarks;
    @Schema(name = "menuIds", title = "菜单id列表")
    List<String> menuIds;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(name = Constants.CREATE_TIME, title = "创建时间", hidden = true)
    LocalDateTime createTime;
}
