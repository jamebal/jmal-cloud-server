package com.jmal.clouddisk.model.query;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jmal
 * @Description 用户查询条件
 * @Date 2021/1/10 2:03 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema
public class QueryUserDTO extends QueryBaseDTO {
    @Schema(name = "username", title = "用户账号")
    String username;
    @Schema(name = "showName", title = "用户名")
    String showName;
}
