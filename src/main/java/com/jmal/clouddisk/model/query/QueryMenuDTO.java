package com.jmal.clouddisk.model.query;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jmal
 * @Description 菜单查询条件
 * @Date 2021/1/10 2:03 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema
public class QueryMenuDTO extends QueryBaseDTO {
    @Schema(name = "name", title = "菜单名称")
    String name;
    @Schema(name = "code", title = "菜单地址")
    String path;
    @Schema(name = "roleId", title = "角色Id")
    String roleId;
    @Schema(name = "userId", title = "userId")
    String userId;
}
