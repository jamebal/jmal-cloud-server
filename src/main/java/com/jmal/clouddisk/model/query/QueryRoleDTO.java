package com.jmal.clouddisk.model.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Description 角色查询条件
 * @blame jmal
 * @Date 2021/1/10 2:03 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel
public class QueryRoleDTO extends QueryBaseDTO {
    @ApiModelProperty(name = "name", value = "角色名称")
    String name;
    @ApiModelProperty(name = "code", value = "角色标识")
    String code;
    @ApiModelProperty(name = "remarks", value = "备注")
    String remarks;
}
