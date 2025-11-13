package com.jmal.clouddisk.model.dto;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.query.QueryBaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema
public class QueryUserDTO extends QueryBaseDTO implements Reflective {
    @Schema(name = "username", title = "用户账号")
    String username;
    @Schema(name = "showName", title = "用户名")
    String showName;
}
