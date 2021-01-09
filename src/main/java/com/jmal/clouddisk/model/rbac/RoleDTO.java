package com.jmal.clouddisk.model.rbac;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 角色传输对象
 * @blame jmal
 * @Date 2021/1/7 7:41 下午
 */
@Data
@Valid
@ApiModel
public class RoleDTO {
    String id;
    @NotNull(message = "角色名称不能为空")
    @ApiModelProperty(name = "name", value = "角色名称", required = true)
    String name;
    @NotNull(message = "角色标识不能为空")
    @ApiModelProperty(name = "code", value = "角色标识", required = true)
    String code;
    @ApiModelProperty(name = "remarks", value = "备注")
    String remarks;
    @ApiModelProperty(name = "menuIds", value = "菜单id列表")
    List<String> menuIds;
    @ApiModelProperty(hidden = true)
    LocalDateTime createTime;
}
