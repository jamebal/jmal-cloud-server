package com.jmal.clouddisk.model.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Description 菜单查询条件
 * @blame jmal
 * @Date 2021/1/10 2:03 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel
public class QueryMenuDTO extends QueryBaseDTO {
    @ApiModelProperty(name = "name", value = "菜单名称")
    String name;
    @ApiModelProperty(name = "code", value = "菜单地址")
    String path;
    @ApiModelProperty(name = "roleId", value = "角色Id")
    String roleId;
    @ApiModelProperty(name = "userId", value = "userId")
    String userId;
}
