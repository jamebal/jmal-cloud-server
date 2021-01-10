package com.jmal.clouddisk.model.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Description 用户查询条件
 * @blame jmal
 * @Date 2021/1/10 2:03 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel
public class QueryUserDTO extends QueryBaseDTO {
    @ApiModelProperty(name = "username", value = "用户账号")
    String username;
    @ApiModelProperty(name = "showName", value = "用户名")
    String showName;
}
