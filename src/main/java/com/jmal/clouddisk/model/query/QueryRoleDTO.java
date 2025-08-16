package com.jmal.clouddisk.model.query;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jmal
 * @Description 角色查询条件
 * @Date 2021/1/10 2:03 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema
public class QueryRoleDTO extends QueryBaseDTO implements Reflective {
    @Schema(name = "name", title = "角色名称")
    String name;
    @Schema(name = "code", title = "角色标识")
    String code;
    @Schema(name = "remarks", title = "备注")
    String remarks;
}
