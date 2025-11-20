package com.jmal.clouddisk.model.query;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jmal
 * @Description 用户组查询条件
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema
public class QueryGroupDTO extends QueryBaseDTO implements Reflective {
    @Schema(name = "name", title = "组名称")
    String name;
    @Schema(name = "code", title = "组标识")
    String code;
}
